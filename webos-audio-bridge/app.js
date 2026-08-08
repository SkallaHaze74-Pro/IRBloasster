(() => {
  'use strict';

  const player = document.getElementById('player');
  const statusEl = document.getElementById('status');
  const detailEl = document.getElementById('detail');
  const meterFill = document.getElementById('meterFill');
  const sliderThumb = document.getElementById('sliderThumb');
  const volumeValue = document.getElementById('volumeValue');
  const modeLabel = document.getElementById('modeLabel');

  let activeUrl = '';
  let activeVolume = 30;
  let mixEnabled = false;
  let audioContext = null;
  let inputGainNode = null;
  let headroomGainNode = null;
  let analyserNode = null;
  let limiterNode = null;
  let bufferSource = null;
  let liveSocket = null;
  let playbackMode = 'idle';
  let liveNextTime = 0;
  let liveLastPacketAt = 0;
  let liveLastSignalAt = 0;
  let liveLastStatusAt = 0;
  let liveUnderruns = 0;
  let liveDrops = 0;
  let liveRateCorrections = 0;
  let livePendingFrames = 0;

  const liveSources = new Set();
  const livePendingPackets = [];

  const SAMPLE_RATE = 48000;
  const LIVE_PACKETS_PER_BUFFER = 4;
  const LIVE_TARGET_AHEAD_SECONDS = 0.18;
  const LIVE_MIN_AHEAD_SECONDS = 0.075;
  const LIVE_SOFT_MAX_AHEAD_SECONDS = 0.36;
  const LIVE_HARD_MAX_AHEAD_SECONDS = 0.65;
  const LIVE_MAX_PENDING_PACKETS = 20;
  const LIVE_STATUS_INTERVAL_MS = 700;
  const OUTPUT_HEADROOM = 0.86;
  const LITTLE_ENDIAN = new Uint16Array(new Uint8Array([1, 0]).buffer)[0] === 1;

  function setStatus(text, detail) {
    if (statusEl) statusEl.textContent = text || '';
    if (detailEl) detailEl.textContent = detail || '';
  }

  function setMode(mode) {
    playbackMode = mode;
    if (!modeLabel) return;
    if (mode === 'live') modeLabel.textContent = 'LIVE HQ SMOOTH';
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

  function volumeGain() {
    return Math.max(0, Math.min(1, activeVolume / 100));
  }

  function setMusicVolume(volume) {
    activeVolume = Math.max(0, Math.min(100, Number(volume) || 0));
    const percent = `${activeVolume}%`;
    if (meterFill) meterFill.style.width = percent;
    if (sliderThumb) sliderThumb.style.left = percent;
    if (volumeValue) volumeValue.textContent = percent;
    if (inputGainNode && audioContext) {
      try {
        inputGainNode.gain.setTargetAtTime(volumeGain(), audioContext.currentTime, 0.018);
      } catch (_) {}
    }
    if (player) player.volume = volumeGain();
  }

  function createAudioContext() {
    const AudioCtx = window.AudioContext || window.webkitAudioContext;
    if (!AudioCtx) throw new Error('Web Audio API fehlt');
    try {
      return new AudioCtx({ latencyHint: 'playback', sampleRate: SAMPLE_RATE });
    } catch (_) {
      try {
        return new AudioCtx({ latencyHint: 'balanced', sampleRate: SAMPLE_RATE });
      } catch (_) {
        return new AudioCtx();
      }
    }
  }

  function connectOutputGraph(context, inputGain) {
    const analyser = context.createAnalyser();
    analyser.fftSize = 256;
    analyser.smoothingTimeConstant = 0.34;
    analyser.minDecibels = -86;
    analyser.maxDecibels = -6;

    const headroom = context.createGain();
    headroom.gain.value = OUTPUT_HEADROOM;

    const limiter = context.createDynamicsCompressor();
    limiter.threshold.value = -10;
    limiter.knee.value = 20;
    limiter.ratio.value = 2.1;
    limiter.attack.value = 0.006;
    limiter.release.value = 0.24;

    inputGain.connect(analyser);
    analyser.connect(headroom);
    headroom.connect(limiter);
    limiter.connect(context.destination);

    inputGainNode = inputGain;
    headroomGainNode = headroom;
    analyserNode = analyser;
    limiterNode = limiter;
    window.SmartIRAudioAnalyser = analyser;
    window.SmartIRAudioContext = context;
  }

  function notifyVisualizerHealth(peak, bufferMs) {
    const signalPercent = Math.min(100, Math.round(peak * 100));
    try {
      if (window.SmartIRVisualizer && typeof window.SmartIRVisualizer.reportAudioHealth === 'function') {
        window.SmartIRVisualizer.reportAudioHealth({
          playing: playbackMode === 'live',
          bufferMs,
          signalPercent,
          underruns: liveUnderruns,
          drops: liveDrops,
          rateCorrections: liveRateCorrections,
        });
      }
    } catch (_) {}
  }

  function clearPendingLiveAudio() {
    livePendingPackets.length = 0;
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
    liveRateCorrections = 0;

    try { inputGainNode && inputGainNode.disconnect(); } catch (_) {}
    inputGainNode = null;
    try { analyserNode && analyserNode.disconnect(); } catch (_) {}
    analyserNode = null;
    try { headroomGainNode && headroomGainNode.disconnect(); } catch (_) {}
    headroomGainNode = null;
    try { limiterNode && limiterNode.disconnect(); } catch (_) {}
    limiterNode = null;

    window.SmartIRAudioAnalyser = null;
    window.SmartIRAudioContext = null;
    notifyVisualizerHealth(0, 0);

    if (audioContext) {
      try { await audioContext.close(); } catch (_) {}
    }
    audioContext = null;

    if (player) {
      try {
        player.pause();
        player.removeAttribute('src');
        player.load();
      } catch (_) {}
    }
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
      const ok = (buffer) => {
        if (!completed) {
          completed = true;
          resolve(buffer);
        }
      };
      const fail = (error) => {
        if (!completed) {
          completed = true;
          reject(error || new Error('decodeAudioData fehlgeschlagen'));
        }
      };
      try {
        const maybePromise = context.decodeAudioData(encoded.slice(0), ok, fail);
        if (maybePromise && typeof maybePromise.then === 'function') maybePromise.then(ok, fail);
      } catch (error) {
        fail(error);
      }
    });

    const inputGain = context.createGain();
    inputGain.gain.value = volumeGain();
    connectOutputGraph(context, inputGain);

    const source = context.createBufferSource();
    source.buffer = decoded;
    source.connect(inputGain);
    source.onended = () => {
      if (bufferSource === source) setStatus('Musik beendet', 'Stream vollständig abgespielt');
    };

    audioContext = context;
    bufferSource = source;
    setMode('webaudio');
    if (context.state === 'suspended') {
      try { await context.resume(); } catch (_) {}
    }
    source.start(0);
    setStatus('Stream läuft', `${activeVolume}% · Web Audio · sanfter Limiter`);
  }

  async function startHtmlAudio(streamUrl) {
    if (!player) throw new Error('HTML-Audioplayer fehlt');
    setMode('htmlaudio');
    player.src = streamUrl;
    player.load();
    player.volume = volumeGain();
    await player.play();
    setStatus('Media-Stream läuft', `${activeVolume}% · Fallback`);
  }

  function packetMetadata(arrayBuffer) {
    if (!(arrayBuffer instanceof ArrayBuffer) || arrayBuffer.byteLength < 4) return null;
    const usableBytes = arrayBuffer.byteLength - (arrayBuffer.byteLength % 4);
    if (usableBytes < 4) return null;
    const frames = usableBytes / 4;
    let peak = 0;

    if (LITTLE_ENDIAN) {
      const samples = new Int16Array(arrayBuffer, 0, usableBytes / 2);
      for (let index = 0; index < samples.length; index += 8) {
        peak = Math.max(peak, Math.abs(samples[index]) / 32768);
      }
    } else {
      const view = new DataView(arrayBuffer, 0, usableBytes);
      for (let offset = 0; offset + 1 < usableBytes; offset += 16) {
        peak = Math.max(peak, Math.abs(view.getInt16(offset, true)) / 32768);
      }
    }

    return { buffer: arrayBuffer, usableBytes, frames, peak };
  }

  function writePacketToChannels(packet, left, right, cursor) {
    if (LITTLE_ENDIAN) {
      const samples = new Int16Array(packet.buffer, 0, packet.usableBytes / 2);
      let frame = cursor;
      for (let sample = 0; sample + 1 < samples.length; sample += 2) {
        left[frame] = samples[sample] / 32768;
        right[frame] = samples[sample + 1] / 32768;
        frame += 1;
      }
      return frame;
    }

    const view = new DataView(packet.buffer, 0, packet.usableBytes);
    let frame = cursor;
    for (let offset = 0; offset + 3 < packet.usableBytes; offset += 4) {
      left[frame] = view.getInt16(offset, true) / 32768;
      right[frame] = view.getInt16(offset + 2, true) / 32768;
      frame += 1;
    }
    return frame;
  }

  function currentBufferMs() {
    if (!audioContext) return 0;
    const scheduledSeconds = Math.max(0, liveNextTime - audioContext.currentTime);
    const pendingSeconds = livePendingFrames / SAMPLE_RATE;
    return Math.round((scheduledSeconds + pendingSeconds) * 1000);
  }

  function updateLiveStatus(peak, force) {
    if (!audioContext) return;
    const wallNow = Date.now();
    if (peak > 0.0015) liveLastSignalAt = wallNow;
    const bufferMs = currentBufferMs();
    notifyVisualizerHealth(peak, bufferMs);

    if (!force && wallNow - liveLastStatusAt < LIVE_STATUS_INTERVAL_MS) return;
    liveLastStatusAt = wallNow;

    const signalPercent = Math.min(100, Math.round(peak * 100));
    const hasRecentSignal = wallNow - liveLastSignalAt < 1800;
    if (hasRecentSignal) {
      setStatus(
        'LIVE · HQ SMOOTH PCM',
        `${activeVolume}% · Signal ${signalPercent}% · Puffer ${bufferMs} ms · Underrun ${liveUnderruns} · Drop ${liveDrops}`,
      );
    } else {
      setStatus(
        'LIVE · verbunden · kein Audiosignal',
        'Android liefert gerade Stille oder die aktive Musik-App blockiert Playback-Capture.',
      );
    }
  }

  function schedulePendingGroup() {
    if (!audioContext || !inputGainNode || playbackMode !== 'live') return;
    if (livePendingPackets.length < LIVE_PACKETS_PER_BUFFER) return;

    const packets = livePendingPackets.splice(0, LIVE_PACKETS_PER_BUFFER);
    let totalFrames = 0;
    let peak = 0;
    packets.forEach((packet) => {
      totalFrames += packet.frames;
      peak = Math.max(peak, packet.peak);
    });
    livePendingFrames = Math.max(0, livePendingFrames - totalFrames);
    if (!totalFrames) return;

    const context = audioContext;
    const now = context.currentTime;
    let recoveredFromUnderrun = false;

    if (liveNextTime <= 0) liveNextTime = now + LIVE_TARGET_AHEAD_SECONDS;
    let ahead = liveNextTime - now;

    if (ahead < LIVE_MIN_AHEAD_SECONDS) {
      liveUnderruns += 1;
      liveNextTime = now + LIVE_TARGET_AHEAD_SECONDS;
      ahead = LIVE_TARGET_AHEAD_SECONDS;
      recoveredFromUnderrun = true;
    }

    if (ahead > LIVE_HARD_MAX_AHEAD_SECONDS) {
      liveDrops += 1;
      updateLiveStatus(peak, true);
      return;
    }

    let playbackRate = 1;
    if (ahead > LIVE_SOFT_MAX_AHEAD_SECONDS) {
      playbackRate = 1 + Math.min(0.006, (ahead - LIVE_SOFT_MAX_AHEAD_SECONDS) * 0.02);
      liveRateCorrections += 1;
    } else if (ahead < LIVE_TARGET_AHEAD_SECONDS - 0.055 && ahead > LIVE_MIN_AHEAD_SECONDS) {
      playbackRate = 0.998;
      liveRateCorrections += 1;
    }

    const audioBuffer = context.createBuffer(2, totalFrames, SAMPLE_RATE);
    const left = audioBuffer.getChannelData(0);
    const right = audioBuffer.getChannelData(1);
    let cursor = 0;
    packets.forEach((packet) => {
      cursor = writePacketToChannels(packet, left, right, cursor);
    });

    const source = context.createBufferSource();
    source.buffer = audioBuffer;
    source.playbackRate.value = playbackRate;

    let edgeGain = null;
    if (recoveredFromUnderrun) {
      edgeGain = context.createGain();
      edgeGain.gain.setValueAtTime(0, liveNextTime);
      edgeGain.gain.linearRampToValueAtTime(1, liveNextTime + 0.008);
      source.connect(edgeGain);
      edgeGain.connect(inputGainNode);
    } else {
      source.connect(inputGainNode);
    }

    source.onended = () => {
      liveSources.delete(source);
      try { source.disconnect(); } catch (_) {}
      try { edgeGain && edgeGain.disconnect(); } catch (_) {}
    };

    liveSources.add(source);
    source.start(liveNextTime);
    liveNextTime += (totalFrames / SAMPLE_RATE) / playbackRate;
    updateLiveStatus(peak, false);
  }

  function trimPendingQueue() {
    while (livePendingPackets.length >= LIVE_MAX_PENDING_PACKETS) {
      const dropped = livePendingPackets.shift();
      if (dropped) livePendingFrames = Math.max(0, livePendingFrames - dropped.frames);
      liveDrops += 1;
    }
  }

  function queueLivePcm(arrayBuffer) {
    if (!audioContext || !inputGainNode || playbackMode !== 'live') return;
    const packet = packetMetadata(arrayBuffer);
    if (!packet) return;

    liveLastPacketAt = Date.now();
    if (packet.peak > 0.0015) liveLastSignalAt = liveLastPacketAt;
    trimPendingQueue();
    livePendingPackets.push(packet);
    livePendingFrames += packet.frames;

    while (livePendingPackets.length >= LIVE_PACKETS_PER_BUFFER) {
      schedulePendingGroup();
    }
  }

  async function startLiveAudio(streamUrl) {
    if (!/^ws:\/\//i.test(streamUrl)) throw new Error('Ungültige Live-URL');

    await stopPlayers();
    activeUrl = streamUrl;
    await setMix(true);

    const context = createAudioContext();
    const inputGain = context.createGain();
    inputGain.gain.value = volumeGain();
    connectOutputGraph(context, inputGain);

    audioContext = context;
    setMode('live');
    if (context.state === 'suspended') {
      try { await context.resume(); } catch (_) {}
    }

    const socket = new WebSocket(streamUrl);
    socket.binaryType = 'arraybuffer';
    socket.onopen = () => {
      liveNextTime = 0;
      setStatus(
        'LIVE · HQ Smooth verbunden',
        `${activeVolume}% · PCM 48 kHz Stereo · 4×20 ms Bündel · 180 ms Zielpuffer`,
      );
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
    setStatus('Gestoppt', 'Visualizer bleibt ruhig; TV-Audio bleibt unverändert');
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

  window.setInterval(() => {
    if (playbackMode !== 'live' || !liveSocket || liveSocket.readyState !== 1) return;
    if (liveLastPacketAt && Date.now() - liveLastPacketAt > 1800) {
      notifyVisualizerHealth(0, currentBufferMs());
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

  setMusicVolume(activeVolume);
  setMode('idle');
  setStatus('Bridge bereit', 'Ruhiger Start · SmartIR wartet auf Audio');
  applyCommand(initialParams());
})();
