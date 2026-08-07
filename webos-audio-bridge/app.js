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
  let liveNextTime = 0;
  let liveLastPacketAt = 0;
  let liveLastSignalAt = 0;
  let liveLastStatusAt = 0;
  let liveUnderruns = 0;
  let liveDrops = 0;
  let livePendingFrames = 0;
  let eqFrequencyData = null;

  const liveSources = new Set();
  const livePendingChunks = [];

  const EQ_BARS = 28;
  const EQ_INTERVAL_MS = 50;
  const SAMPLE_RATE = 48000;
  const LIVE_PACKETS_PER_BUFFER = 3;
  const LIVE_TARGET_AHEAD_SECONDS = 0.12;
  const LIVE_MIN_AHEAD_SECONDS = 0.035;
  const LIVE_MAX_AHEAD_SECONDS = 0.24;
  const eqBars = [];
  const eqLevels = new Float32Array(EQ_BARS);
  const eqBandValues = new Float32Array(EQ_BARS);

  function createEqualizer() {
    if (!visualizer) return;
    visualizer.textContent = '';
    for (let index = 0; index < EQ_BARS; index += 1) {
      const bar = document.createElement('span');
      bar.className = 'eq-bar';
      const hue = Math.round((index / Math.max(1, EQ_BARS - 1)) * 320 + 300) % 360;
      bar.style.setProperty('--bar-color', `hsl(${hue}, 96%, 58%)`);
      bar.style.setProperty('--bar-mid', `hsl(${(hue + 22) % 360}, 100%, 68%)`);
      bar.style.height = '118px';
      bar.style.transform = 'scaleY(.055)';
      bar.style.transformOrigin = 'bottom center';
      bar.style.transition = 'transform 46ms linear, opacity 90ms linear';
      bar.style.willChange = 'transform, opacity';
      visualizer.appendChild(bar);
      eqBars.push(bar);
    }
  }

  function setStatus(text, detail) {
    if (statusEl) statusEl.textContent = text || '';
    if (detailEl) detailEl.textContent = detail || '';
  }

  function setMode(mode) {
    playbackMode = mode;
    if (!modeLabel) return;
    if (mode === 'live') modeLabel.textContent = 'LIVE STABLE';
    else if (mode === 'webaudio') modeLabel.textContent = 'STREAM';
    else if (mode === 'htmlaudio') modeLabel.textContent = 'MEDIA';
    else modeLabel.textContent = 'READY';
  }

  function activateApp() {
    try {
      if (window.webOSSystem && typeof window.webOSSystem.activate === 'function') {
        window.webOSSystem.activate();
        return;
      }
    } catch (_) {}
    try {
      if (window.PalmSystem && typeof window.PalmSystem.activate === 'function') {
        window.PalmSystem.activate();
      }
    } catch (_) {}
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
          if (response.returnValue === false) {
            reject(new Error(response.errorText || response.errorCode || 'Luna-Aufruf abgelehnt'));
          } else {
            resolve(response);
          }
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
    if (meterFill) meterFill.style.width = percent;
    if (sliderThumb) sliderThumb.style.left = percent;
    if (volumeValue) volumeValue.textContent = percent;
    if (gainNode && audioContext) {
      try { gainNode.gain.setValueAtTime(activeVolume / 100, audioContext.currentTime); } catch (_) {}
    }
    if (player) player.volume = activeVolume / 100;
  }

  function createAudioContext() {
    const AudioCtx = window.AudioContext || window.webkitAudioContext;
    if (!AudioCtx) throw new Error('Web Audio API fehlt');
    try {
      return new AudioCtx({ latencyHint: 'interactive', sampleRate: SAMPLE_RATE });
    } catch (_) {
      return new AudioCtx();
    }
  }

  function connectOutputGraph(context, gain) {
    const analyser = context.createAnalyser();
    analyser.fftSize = 512;
    analyser.smoothingTimeConstant = 0.24;
    analyser.minDecibels = -84;
    analyser.maxDecibels = -3;

    const compressor = context.createDynamicsCompressor();
    compressor.threshold.value = -3;
    compressor.knee.value = 5;
    compressor.ratio.value = 4;
    compressor.attack.value = 0.004;
    compressor.release.value = 0.18;

    gain.connect(analyser);
    analyser.connect(compressor);
    compressor.connect(context.destination);
    analyserNode = analyser;
    compressorNode = compressor;
    eqFrequencyData = new Uint8Array(analyser.frequencyBinCount);
  }

  function setEqLevel(index, target, opacityBase) {
    const previous = eqLevels[index] || 0;
    const next = target > previous
      ? previous * 0.18 + target * 0.82
      : previous * 0.68 + target * 0.32;
    eqLevels[index] = next;
    const scale = Math.max(0.055, Math.min(1, 0.055 + next * 0.945));
    eqBars[index].style.transform = `scaleY(${scale.toFixed(3)})`;
    eqBars[index].style.opacity = `${Math.max(opacityBase, 0.32 + next * 0.68).toFixed(2)}`;
  }

  function renderEqualizer() {
    if (!eqBars.length) return;

    if (analyserNode && audioContext && audioContext.state !== 'closed') {
      if (!eqFrequencyData || eqFrequencyData.length !== analyserNode.frequencyBinCount) {
        eqFrequencyData = new Uint8Array(analyserNode.frequencyBinCount);
      }
      analyserNode.getByteFrequencyData(eqFrequencyData);

      const minBin = 1;
      const maxBin = Math.min(eqFrequencyData.length - 1, 170);
      const ratio = Math.max(2, maxBin / minBin);
      let framePeak = 0;

      for (let index = 0; index < EQ_BARS; index += 1) {
        const start = Math.max(minBin, Math.floor(minBin * Math.pow(ratio, index / EQ_BARS)));
        const end = Math.min(maxBin, Math.max(start, Math.floor(minBin * Math.pow(ratio, (index + 1) / EQ_BARS))));
        let peak = 0;
        let sum = 0;
        let count = 0;
        for (let bin = start; bin <= end; bin += 1) {
          const value = eqFrequencyData[bin] || 0;
          peak = Math.max(peak, value);
          sum += value;
          count += 1;
        }
        const average = count ? sum / count : peak;
        const mixed = peak * 0.62 + average * 0.38;
        eqBandValues[index] = mixed;
        framePeak = Math.max(framePeak, mixed);
      }

      if (framePeak < 4) {
        for (let index = 0; index < EQ_BARS; index += 1) {
          setEqLevel(index, 0.012 + (index % 3) * 0.005, 0.32);
        }
        return;
      }

      const amplitude = Math.max(0.12, Math.min(1, (framePeak - 4) / 205));
      for (let index = 0; index < EQ_BARS; index += 1) {
        const relative = Math.max(0, Math.min(1, eqBandValues[index] / framePeak));
        const shaped = amplitude * Math.pow(relative, 0.72);
        setEqLevel(index, shaped, 0.42);
      }
      return;
    }

    const time = Date.now() / 440;
    for (let index = 0; index < EQ_BARS; index += 1) {
      const wave = (Math.sin(time + index * 0.61) + Math.sin(time * 0.67 + index * 0.29) + 2) / 4;
      const target = playbackMode === 'idle' ? 0.025 + wave * 0.055 : 0.05 + wave * 0.13;
      setEqLevel(index, target, playbackMode === 'idle' ? 0.32 : 0.48);
    }
  }

  function clearPendingLiveAudio() {
    livePendingChunks.length = 0;
    livePendingFrames = 0;
  }

  function stopLiveSources() {
    liveSources.forEach((source) => {
      try { source.stop(0); } catch (_) {}
      try { source.disconnect(); } catch (_) {}
    });
    liveSources.clear();
  }

  async function stopPlayers() {
    try { bufferSource && bufferSource.stop(0); } catch (_) {}
    try { bufferSource && bufferSource.disconnect(); } catch (_) {}
    bufferSource = null;

    try { liveSocket && liveSocket.close(); } catch (_) {}
    liveSocket = null;
    stopLiveSources();
    clearPendingLiveAudio();
    liveNextTime = 0;
    liveLastPacketAt = 0;
    liveLastSignalAt = 0;
    liveLastStatusAt = 0;
    liveUnderruns = 0;
    liveDrops = 0;

    try { gainNode && gainNode.disconnect(); } catch (_) {}
    gainNode = null;
    try { analyserNode && analyserNode.disconnect(); } catch (_) {}
    analyserNode = null;
    eqFrequencyData = null;
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
    setStatus('Lade Musik …', streamUrl);
    const encoded = await fetchAudioBuffer(streamUrl);
    const context = createAudioContext();
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
    source.onended = () => {
      if (bufferSource === source) setStatus('Musik beendet', 'Stream vollständig abgespielt');
    };
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

  function updateLiveStatus(peak) {
    if (!audioContext) return;
    const wallNow = Date.now();
    liveLastPacketAt = wallNow;
    if (peak > 0.002) liveLastSignalAt = wallNow;
    if (wallNow - liveLastStatusAt < 500) return;

    liveLastStatusAt = wallNow;
    const scheduledSeconds = Math.max(0, liveNextTime - audioContext.currentTime);
    const pendingSeconds = livePendingFrames / SAMPLE_RATE;
    const bufferMs = Math.round((scheduledSeconds + pendingSeconds) * 1000);
    const signalPercent = Math.min(100, Math.round(peak * 100));
    const hasRecentSignal = wallNow - liveLastSignalAt < 1600;

    if (hasRecentSignal) {
      setStatus(
        'LIVE · STABLE PCM',
        `${activeVolume}% · Signal ${signalPercent}% · Puffer ${bufferMs} ms · Underrun ${liveUnderruns} · Drop ${liveDrops}`,
      );
    } else {
      setStatus(
        'LIVE · verbunden · kein Audiosignal',
        'Android liefert nur Stille. Die aktive Musik-App kann Playback-Capture blockieren.',
      );
    }
  }

  function schedulePendingGroup() {
    if (!audioContext || !gainNode || playbackMode !== 'live') return;
    if (livePendingChunks.length < LIVE_PACKETS_PER_BUFFER) return;

    const chunks = livePendingChunks.splice(0, LIVE_PACKETS_PER_BUFFER);
    let totalFrames = 0;
    let peak = 0;
    chunks.forEach((chunk) => {
      totalFrames += chunk.frames;
      peak = Math.max(peak, chunk.peak);
    });
    livePendingFrames = Math.max(0, livePendingFrames - totalFrames);
    if (!totalFrames) return;

    const context = audioContext;
    const now = context.currentTime;
    if (liveNextTime <= 0) liveNextTime = now + LIVE_TARGET_AHEAD_SECONDS;
    let ahead = liveNextTime - now;

    if (ahead < LIVE_MIN_AHEAD_SECONDS) {
      liveUnderruns += 1;
      liveNextTime = now + LIVE_TARGET_AHEAD_SECONDS;
      ahead = liveNextTime - now;
    }

    if (ahead > LIVE_MAX_AHEAD_SECONDS) {
      liveDrops += 1;
      updateLiveStatus(peak);
      return;
    }

    const audioBuffer = context.createBuffer(2, totalFrames, SAMPLE_RATE);
    const left = audioBuffer.getChannelData(0);
    const right = audioBuffer.getChannelData(1);
    let cursor = 0;
    chunks.forEach((chunk) => {
      left.set(chunk.left, cursor);
      right.set(chunk.right, cursor);
      cursor += chunk.frames;
    });

    const source = context.createBufferSource();
    source.buffer = audioBuffer;
    source.connect(gainNode);
    source.onended = () => {
      liveSources.delete(source);
      try { source.disconnect(); } catch (_) {}
    };
    liveSources.add(source);
    source.start(liveNextTime);
    liveNextTime += totalFrames / SAMPLE_RATE;
    updateLiveStatus(peak);
  }

  function queueLivePcm(arrayBuffer) {
    if (!audioContext || !gainNode || playbackMode !== 'live') return;
    const pcm = decodePcm16Stereo(arrayBuffer);
    if (!pcm.frames) return;
    liveLastPacketAt = Date.now();
    if (pcm.peak > 0.002) liveLastSignalAt = liveLastPacketAt;
    livePendingChunks.push(pcm);
    livePendingFrames += pcm.frames;

    while (livePendingChunks.length >= LIVE_PACKETS_PER_BUFFER) {
      schedulePendingGroup();
    }
  }

  async function startLiveAudio(streamUrl) {
    if (!/^ws:\/\//i.test(streamUrl)) throw new Error('Ungültige Live-URL');

    await stopPlayers();
    activeUrl = streamUrl;
    await setMix(true);

    const context = createAudioContext();
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
      liveNextTime = 0;
      setStatus('LIVE · stabil verbunden', `${activeVolume}% · PCM 48 kHz Stereo · 3×20 ms Bündel · 120 ms Zielpuffer`);
    };
    socket.onmessage = (event) => {
      if (!(event.data instanceof ArrayBuffer)) return;
      queueLivePcm(event.data);
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
      try {
        await startHtmlAudio(activeUrl);
      } catch (htmlError) {
        setMode('idle');
        setStatus(
          'Audio konnte nicht starten',
          `WebAudio: ${String(webAudioError && webAudioError.message || webAudioError)} · HTML: ${String(htmlError && htmlError.message || htmlError)}`,
        );
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
    if (action === 'stop') {
      await stopStream();
      return;
    }
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
      try {
        await startLiveAudio(command.streamUrl || activeUrl);
      } catch (error) {
        setStatus('LIVE-Audio konnte nicht starten', String(error && error.message || error));
      }
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

  if (player) {
    player.addEventListener('playing', () => {
      if (playbackMode === 'htmlaudio') setStatus('Media-Stream läuft', `${activeVolume}% · Fallback`);
    });
    player.addEventListener('waiting', () => {
      if (playbackMode === 'htmlaudio') setStatus('Puffert …', activeUrl);
    });
    player.addEventListener('error', () => {
      const code = player.error ? player.error.code : '?';
      if (playbackMode === 'htmlaudio') setStatus('Audiofehler', `MediaError ${code} · ${activeUrl}`);
    });
  }

  document.addEventListener('visibilitychange', () => {
    if (activeUrl) setMix(true);
    if (audioContext && audioContext.state === 'suspended') {
      try { audioContext.resume(); } catch (_) {}
    }
  }, true);

  document.addEventListener('webOSRelaunch', async (event) => {
    await applyCommand(parseParams(event && event.detail));
    activateApp();
  }, true);

  document.addEventListener('webOSLaunch', async (event) => {
    await applyCommand(parseParams(event && event.detail));
    activateApp();
  }, true);

  window.setInterval(renderEqualizer, EQ_INTERVAL_MS);

  window.setInterval(() => {
    if (playbackMode !== 'live' || !liveSocket || liveSocket.readyState !== 1) return;
    if (liveLastPacketAt && Date.now() - liveLastPacketAt > 1600) {
      setStatus('LIVE · wartet auf PCM', 'WLAN verbunden, aber vom Handy kommen gerade keine Audiopakete.');
    }
  }, 800);

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
  renderEqualizer();
  setStatus('Bridge bereit', 'SmartIR wartet auf Audio');
  applyCommand(initialParams());
})();
