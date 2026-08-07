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
  let bufferSource = null;
  let liveSocket = null;
  let liveProcessor = null;
  let liveQueue = [];
  let liveOffset = 0;
  let playbackMode = 'idle';
  let animationFrame = 0;
  let lastVisualUpdate = 0;

  const EQ_BARS = 36;
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
    if (mode === 'live') modeLabel.textContent = 'LIVE';
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
    analyser.smoothingTimeConstant = 0.76;
    analyser.minDecibels = -88;
    analyser.maxDecibels = -18;
    gain.connect(analyser);
    analyser.connect(context.destination);
    analyserNode = analyser;
  }

  function renderEqualizer(now) {
    animationFrame = requestAnimationFrame(renderEqualizer);
    if (!eqBars.length || now - lastVisualUpdate < 32) return;
    lastVisualUpdate = now;

    if (analyserNode && audioContext && audioContext.state !== 'closed') {
      const bins = new Uint8Array(analyserNode.frequencyBinCount);
      analyserNode.getByteFrequencyData(bins);
      for (let i = 0; i < eqBars.length; i += 1) {
        const start = Math.floor((i / eqBars.length) * Math.min(bins.length, 70));
        const end = Math.min(bins.length - 1, start + 2);
        let value = 0;
        for (let b = start; b <= end; b += 1) value = Math.max(value, bins[b] || 0);
        const normalized = Math.max(0.04, Math.min(1, value / 255));
        const shaped = Math.pow(normalized, 0.72);
        eqBars[i].style.height = `${8 + shaped * 116}px`;
        eqBars[i].style.opacity = `${0.42 + shaped * 0.58}`;
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
    if (liveProcessor) {
      try { liveProcessor.disconnect(); } catch (_) {}
      liveProcessor.onaudioprocess = null;
    }
    liveProcessor = null;
    liveQueue = [];
    liveOffset = 0;
    try { gainNode && gainNode.disconnect(); } catch (_) {}
    gainNode = null;
    try { analyserNode && analyserNode.disconnect(); } catch (_) {}
    analyserNode = null;
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

  function decodePcm16(arrayBuffer) {
    const view = new DataView(arrayBuffer);
    const count = Math.floor(view.byteLength / 2);
    const values = new Float32Array(count);
    for (let i = 0; i < count; i += 1) values[i] = view.getInt16(i * 2, true) / 32768;
    return values;
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

    const processor = context.createScriptProcessor(2048, 0, 2);
    processor.onaudioprocess = (event) => {
      const left = event.outputBuffer.getChannelData(0);
      const right = event.outputBuffer.getChannelData(1);
      for (let frame = 0; frame < left.length; frame += 1) {
        while (liveQueue.length && liveOffset >= liveQueue[0].length) {
          liveQueue.shift();
          liveOffset = 0;
        }
        if (!liveQueue.length) {
          left[frame] = 0;
          right[frame] = 0;
          continue;
        }
        const chunk = liveQueue[0];
        left[frame] = chunk[liveOffset] || 0;
        right[frame] = chunk[liveOffset + 1] || left[frame];
        liveOffset += 2;
      }
    };
    processor.connect(gain);

    audioContext = context;
    gainNode = gain;
    liveProcessor = processor;
    setMode('live');
    if (context.state === 'suspended') { try { await context.resume(); } catch (_) {} }

    const socket = new WebSocket(streamUrl);
    socket.binaryType = 'arraybuffer';
    socket.onopen = () => setStatus('LIVE · verbunden', `${activeVolume}% · Handy → WLAN → TV`);
    socket.onmessage = (event) => {
      if (!(event.data instanceof ArrayBuffer)) return;
      liveQueue.push(decodePcm16(event.data));
      while (liveQueue.length > 50) {
        liveQueue.shift();
        liveOffset = 0;
      }
    };
    socket.onerror = () => setStatus('LIVE-Verbindung fehlerhaft', streamUrl);
    socket.onclose = () => { if (playbackMode === 'live') setStatus('LIVE-Verbindung beendet', streamUrl); };
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
