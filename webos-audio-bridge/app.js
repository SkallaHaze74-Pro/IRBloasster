(() => {
  'use strict';

  const player = document.getElementById('player');
  const statusEl = document.getElementById('status');
  const detailEl = document.getElementById('detail');
  const meterFill = document.getElementById('meterFill');
  const sliderThumb = document.getElementById('sliderThumb');
  const volumeValue = document.getElementById('volumeValue');
  const modeLabel = document.getElementById('modeLabel');
  const visualizer = document.getElementById('visualizer');

  let activeUrl = '';
  let activeVolume = 30;
  let mixEnabled = false;
  let audioContext = null;
  let gainNode = null;
  let analyserNode = null;
  let compressorNode = null;
  let bufferSource = null;
  let liveSocket = null;
  let playbackMode = 'idle';
  let animationFrame = 0;
  let lastVisualUpdate = 0;
  let liveNextTime = 0;
  let liveLastPacketAt = 0;
  let liveLastSignalAt = 0;
  let liveLastStatusAt = 0;
  let liveUnderruns = 0;
  const liveSources = new Set();

  const EQ_BARS = 36;
  const SAMPLE_RATE = 48000;
  const LIVE_PREBUFFER_SECONDS = 0.12;
  const LIVE_MIN_AHEAD_SECONDS = 0.035;
  const LIVE_MAX_AHEAD_SECONDS = 0.55;
  const eqBars = [];

  function createEqualizer() {
    if (!visualizer) return;
    visualizer.textContent = '';
    for (let i = 0; i < EQ_BARS; i += 1) {
      const bar = document.createElement('span');
      bar.className = 'eq-bar';
      const hue = Math.round((i / Math.max(1, EQ_BARS - 1)) * 320 + 300) % 360;
      bar.style.setProperty('--bar-color', `hsl(${hue}, 96%, 58%)`);
      bar.style.setProperty('--bar-mid', `hsl(${(hue + 22) % 360}, 100%, 68%)`);
      visualizer.appendChild(bar);
      eqBars.push(bar);
    }
  }

  function setStatus(text, detail) {
    statusEl.textContent = text || '';
    detailEl.textContent = detail || '';
  }

  function setMode(mode) {
    playbackMode = mode;
    if (!modeLabel) return;
    if (mode === 'live') modeLabel.textContent = 'LIVE HQ';
    else if (mode === 'webaudio') modeLabel.textContent = 'STREAM';
    else if (mode === 'htmlaudio') modeLabel.textContent = 'MEDIA';
    else modeLabel.textContent = 'READY';
  }

  function bridgeCall(uri, payload) {
    return new Promise((resolve, reject) => {
      if (!window.PalmServiceBridge) {
        reject(new Error('PalmServiceBridge nicht verfügbar'));
        return;
      }
      const bridge = new window.PalmServiceBridge();
      let settled = false;
      bridge.onservicecallback = (raw) => {
        if (settled) return;
        settled = true;
        try {
          const response = raw ? JSON.parse(raw) : {};
          if (response.returnValue === false) reject(new Error(response.errorText || response.errorCode || 'Luna-Aufruf abgelehnt'));
          else resolve(response);
        } catch (_) {
          resolve({ raw });
        }
      };
      try {
        bridge.call(uri, JSON.stringify(payload || {}));
      } catch (error) {
        reject(error);
      }
    });
  }

  async function setMix(on) {
    try {
      await bridgeCall('luna://com.webos.service.audio/tv/mixDigitalSoundOutput', { mix: !!on });
      mixEnabled = !!on;
      return true;
    } catch (error) {
      setStatus('Mix-Policy abgelehnt', String(error && error.message || error));
      return false;
    }
  }

  function setMusicVolume(volume) {
    activeVolume = Math.max(0, Math.min(100, Number(volume) || 0));
    const percent = `${activeVolume}%`;
    meterFill.style.width = percent;
    if (sliderThumb) sliderThumb.style.left = percent;
    if (volumeValue) volumeValue.textContent = percent;
    if (gainNode && audioContext) gainNode.gain.setValueAtTime(activeVolume / 100, audioContext.currentTime);
    player.volume = activeVolume / 100;
  }

  function connectOutputGraph(context, gain) {
    const analyser = context.createAnalyser();
    analyser.fftSize = 256;
    analyser.smoothingTimeConstant = 0.72;
    analyser.minDecibels = -92;
    analyser.maxDecibels = -14;

    const compressor = context.createDynamicsCompressor();
    compressor.threshold.value = -3;
    compressor.knee.value = 5;
    compressor.ratio.value = 4;
    compressor.attack.value = 0.003;
    compressor.release.value = 0.16;

    gain.connect(analyser);
    analyser.connect(compressor);
    compressor.connect(context.destination);
    analyserNode = analyser;
    compressorNode = compressor;
  }

  function renderEqualizer(now) {
    animationFrame = requestAnimationFrame(renderEqualizer);
    if (!eqBars.length || now - lastVisualUpdate < 32) return;
    lastVisualUpdate = now;

    if (analyserNode && audioContext && audioContext.state !== 'closed') {
      const bins = new Uint8Array(analyserNode.frequencyBinCount);
      analyserNode.getByteFrequencyData(bins);
      let overallPeak = 0;
      for (let i = 0; i < Math.min(bins.length, 70); i += 1) overallPeak = Math.max(overallPeak, bins[i] || 0);
      const silent = overallPeak < 3;

      for (let i = 0; i < eqBars.length; i += 1) {
        const start = Math.floor((i / eqBars.length) * Math.min(bins.length, 70));
        const end = Math.min(bins.length - 1, start + 2);
        let value = 0;
        for (let b = start; b <= end; b += 1) value = Math.max(value, bins[b] || 0);
        if (silent) {
          eqBars[i].style.height = `${7 + (i % 4)}px`;
          eqBars[i].style.opacity = '.34';
          continue;
        }
        const normalized = Math.max(0, Math.min(1, value / 255));
        const shaped = Math.pow(normalized, 0.64);
        eqBars[i].style.height = `${8 + shaped * 118}px`;
        eqBars[i].style.opacity = `${0.45 + shaped * 0.55}`;
      }
      return;
    }

    const time = now / 470;
    for (let i = 0; i < eqBars.length; i += 1) {
      const wave = (Math.sin(time + i * 0.63) + Math.sin(time * 0.67 + i * 0.31) + 2) / 4;
      const height = playbackMode === 'idle' ? 7 + wave * 18 : 12 + wave * 34;
      eqBars[i].style.height = `${height}px`;
      eqBars[i].style.opacity = playbackMode === 'idle' ? '.42' : '.72';
    }
  }

  async function stopPlayers() {
    try { bufferSource && bufferSource.stop(0); } catch (_) {}
    try { bufferSource && bufferSource.disconnect(); } catch (_) {}
    bufferSource = null;

    try { liveSocket && liveSocket.close(); } catch (_) {}
    liveSocket = null;

    liveSources.forEach((source) => {
      try { source.stop(0); } catch (_) {}
      try { source.disconnect(); } catch (_) {}
    });
    liveSources.clear();
    liveNextTime = 0;
    liveLastPacketAt = 0;
    liveLastSignalAt = 0;
    liveLastStatusAt = 0;
    liveUnderruns = 0;

    try { gainNode && gainNode.disconnect(); } catch (_) {}
    gainNode = null;
    try { analyserNode && analyserNode.disconnect(); } catch (_) {}
    analyserNode = null;
    try { compressorNode && compressorNode.disconnect(); } catch (_) {}
    compressorNode = null;

    if (audioContext) {
      try { await audioContext.close(); } catch (_) {}
    }
    audioContext = null;

    try {
      player.pause();
      player.removeAttribute('src');
      player.load();
    } catch (_) {}
    setMode('idle');
  }

  async function fetchAudioBuffer(streamUrl) {
    const response = await fetch(streamUrl, { cache: 'no-store' });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const bytes = await response.arrayBuffer();
    if (!bytes || bytes.byteLength === 0) throw new Error('Leere Audiodatei');
    return bytes;
  }

  async function startWebAudio(streamUrl) {
    const AudioCtx = window.AudioContext || window.webkitAudioContext;
    if (!AudioCtx) throw new Error('Web Audio API fehlt');
    setStatus('Lade Musik …', streamUrl);
    const encoded = await fetchAudioBuffer(streamUrl);
    const context = new AudioCtx();
    const decoded = await new Promise((resolve, reject) => {
      let completed = false;
      const ok = (buffer) => { if (!completed) { completed = true; resolve(buffer); } };
      const fail = (error) => { if (!completed) { completed = true; reject(error || new Error('decodeAudioData fehlgeschlagen')); } };
      try {
        const maybePromise = context.decodeAudioData(encoded.slice(0), ok, fail);
        if (maybePromise && typeof maybePromise.then === 'function') maybePromise.then(ok, fail);
      } catch (error) { fail(error); }
    });

    const gain = context.createGain();
    gain.gain.value = activeVolume / 100;
    connectOutputGraph(context, gain);

    const source = context.createBufferSource();
    source.buffer = decoded;
    source.connect(gain);
    source.onended = () => { if (bufferSource === source) setStatus('Musik beendet', 'Stream vollständig abgespielt'); };
    audioContext = context;
    gainNode = gain;
    bufferSource = source;
    setMode('webaudio');
    if (context.state === 'suspended') { try { await context.resume(); } catch (_) {} }
    source.start(0);
    setStatus('Stream läuft', `${activeVolume}% · Web Audio`);
  }

  async function startHtmlAudio(streamUrl) {
    setMode('htmlaudio');
    player.src = streamUrl;
    player.load();
    player.volume = activeVolume / 100;
    await player.play();
    setStatus('Media-Stream läuft', `${activeVolume}% · Fallback`);
  }

  function decodePcm16Stereo(arrayBuffer) {
    const view = new DataView(arrayBuffer);
    const frames = Math.floor(view.byteLength / 4);
    const left = new Float32Array(frames);
    const right = new Float32Array(frames);
    let peak = 0;

    for (let frame = 0; frame < frames; frame += 1) {
      const offset = frame * 4;
      const l = view.getInt16(offset, true) / 32768;
      const r = view.getInt16(offset + 2, true) / 32768;
      left[frame] = l;
      right[frame] = r;
      peak = Math.max(peak, Math.abs(l), Math.abs(r));
    }
    return { frames, left, right, peak };
  }

  function scheduleLivePcm(arrayBuffer) {
    if (!audioContext || !gainNode || playbackMode !== 'live') return;
    const pcm = decodePcm16Stereo(arrayBuffer);
    if (!pcm.frames) return;

    const context = audioContext;
    const now = context.currentTime;
    if (liveNextTime < now + LIVE_MIN_AHEAD_SECONDS || liveNextTime > now + LIVE_MAX_AHEAD_SECONDS) {
      if (liveNextTime > 0 && liveNextTime < now + LIVE_MIN_AHEAD_SECONDS) liveUnderruns += 1;
      liveNextTime = now + LIVE_PREBUFFER_SECONDS;
    }

    const audioBuffer = context.createBuffer(2, pcm.frames, SAMPLE_RATE);
    audioBuffer.getChannelData(0).set(pcm.left);
    audioBuffer.getChannelData(1).set(pcm.right);

    const source = context.createBufferSource();
    source.buffer = audioBuffer;
    source.connect(gainNode);
    source.onended = () => {
      liveSources.delete(source);
      try { source.disconnect(); } catch (_) {}
    };
    liveSources.add(source);
    source.start(liveNextTime);
    liveNextTime += pcm.frames / SAMPLE_RATE;

    const wallNow = Date.now();
    liveLastPacketAt = wallNow;
    if (pcm.peak > 0.002) liveLastSignalAt = wallNow;

    if (wallNow - liveLastStatusAt >= 800) {
      liveLastStatusAt = wallNow;
      const bufferMs = Math.max(0, Math.round((liveNextTime - context.currentTime) * 1000));
      const signalPercent = Math.min(100, Math.round(pcm.peak * 100));
      const hasRecentSignal = wallNow - liveLastSignalAt < 1800;
      if (hasRecentSignal) {
        setStatus(
          'LIVE · HQ PCM',
          `${activeVolume}% · Signal ${signalPercent}% · Puffer ${bufferMs} ms · Resync ${liveUnderruns}`,
        );
      } else {
        setStatus(
          'LIVE · verbunden · kein Audiosignal',
          'Android liefert nur Stille. Die aktive Musik-App kann Playback-Capture blockieren.',
        );
      }
    }
  }

  async function startLiveAudio(streamUrl) {
    const AudioCtx = window.AudioContext || window.webkitAudioContext;
    if (!AudioCtx) throw new Error('Web Audio API fehlt');
    if (!/^ws:\/\//i.test(streamUrl)) throw new Error('Ungültige Live-URL');

    await stopPlayers();
    activeUrl = streamUrl;
    await setMix(true);

    const context = new AudioCtx();
    const gain = context.createGain();
    gain.gain.value = activeVolume / 100;
    connectOutputGraph(context, gain);

    audioContext = context;
    gainNode = gain;
    setMode('live');
    if (context.state === 'suspended') { try { await context.resume(); } catch (_) {} }

    const socket = new WebSocket(streamUrl);
    socket.binaryType = 'arraybuffer';
    socket.onopen = () => {
      liveNextTime = context.currentTime + LIVE_PREBUFFER_SECONDS;
      setStatus('LIVE · HQ verbunden', `${activeVolume}% · PCM 48 kHz Stereo · 120 ms Jitterpuffer`);
    };
    socket.onmessage = (event) => {
      if (!(event.data instanceof ArrayBuffer)) return;
      scheduleLivePcm(event.data);
    };
    socket.onerror = () => setStatus('LIVE-Verbindung fehlerhaft', streamUrl);
    socket.onclose = () => {
      if (playbackMode === 'live') setStatus('LIVE-Verbindung beendet', streamUrl);
    };
    liveSocket = socket;
  }

  async function startStream(streamUrl, volume) {
    if (!streamUrl || !/^https?:\/\//i.test(streamUrl)) {
      setStatus('Keine gültige Audio-URL', streamUrl || 'streamUrl fehlt');
      return;
    }
    activeUrl = streamUrl;
    setMusicVolume(volume == null ? activeVolume : volume);
    await stopPlayers();
    await setMix(true);
    try {
      await startWebAudio(activeUrl);
    } catch (webAudioError) {
      try { await startHtmlAudio(activeUrl); }
      catch (htmlError) {
        setMode('idle');
        setStatus('Audio konnte nicht starten', `WebAudio: ${String(webAudioError && webAudioError.message || webAudioError)} · HTML: ${String(htmlError && htmlError.message || htmlError)}`);
      }
    }
  }

  async function stopStream() {
    await stopPlayers();
    activeUrl = '';
    await setMix(false);
    setStatus('Gestoppt', 'TV-Audio bleibt unverändert');
  }

  async function applyCommand(params) {
    const command = params || {};
    const action = String(command.action || 'start').toLowerCase();
    if (action === 'stop') { await stopStream(); return; }
    if (action === 'volume') {
      setMusicVolume(command.volume);
      setStatus('Musikpegel geändert', `${activeVolume}% · ${playbackMode}`);
      return;
    }
    if (action === 'ping') {
      setStatus('Bridge bereit', activeUrl ? `${playbackMode} aktiv · ${activeVolume}%` : 'Kein Stream aktiv');
      return;
    }
    setMusicVolume(command.volume == null ? activeVolume : command.volume);
    if (action === 'live') {
      try { await startLiveAudio(command.streamUrl || activeUrl); }
      catch (error) { setStatus('LIVE-Audio konnte nicht starten', String(error && error.message || error)); }
      return;
    }
    await startStream(command.streamUrl || activeUrl, command.volume);
  }

  function parseParams(value) {
    if (!value) return {};
    if (typeof value === 'object') return value;
    try { return JSON.parse(value); } catch (_) { return {}; }
  }

  function initialParams() {
    if (window.webOSSystem && window.webOSSystem.launchParams) return parseParams(window.webOSSystem.launchParams);
    if (window.PalmSystem && window.PalmSystem.launchParams) return parseParams(window.PalmSystem.launchParams);
    return {};
  }

  player.addEventListener('playing', () => { if (playbackMode === 'htmlaudio') setStatus('Media-Stream läuft', `${activeVolume}% · Fallback`); });
  player.addEventListener('waiting', () => { if (playbackMode === 'htmlaudio') setStatus('Puffert …', activeUrl); });
  player.addEventListener('error', () => {
    const code = player.error ? player.error.code : '?';
    if (playbackMode === 'htmlaudio') setStatus('Audiofehler', `MediaError ${code} · ${activeUrl}`);
  });

  document.addEventListener('visibilitychange', () => {
    if (document.hidden && activeUrl) {
      setMix(true);
      if (audioContext && audioContext.state === 'suspended') { try { audioContext.resume(); } catch (_) {} }
    }
  });

  document.addEventListener('webOSRelaunch', (event) => applyCommand(parseParams(event && event.detail)));

  window.setInterval(() => {
    if (playbackMode !== 'live' || !liveSocket || liveSocket.readyState !== 1) return;
    if (liveLastPacketAt && Date.now() - liveLastPacketAt > 1800) {
      setStatus('LIVE · wartet auf PCM', 'WLAN verbunden, aber vom Handy kommen gerade keine Audiopakete.');
    }
  }, 900);

  window.addEventListener('beforeunload', () => {
    if (mixEnabled) {
      try {
        const bridge = new window.PalmServiceBridge();
        bridge.call('luna://com.webos.service.audio/tv/mixDigitalSoundOutput', JSON.stringify({ mix: false }));
      } catch (_) {}
    }
  });

  createEqualizer();
  setMusicVolume(activeVolume);
  setMode('idle');
  animationFrame = requestAnimationFrame(renderEqualizer);
  setStatus('Bridge bereit', 'SmartIR wartet auf Audio');
  applyCommand(initialParams());
})();
