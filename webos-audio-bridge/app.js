(() => {
  'use strict';

  const player = document.getElementById('player');
  const statusEl = document.getElementById('status');
  const detailEl = document.getElementById('detail');
  const meterFill = document.getElementById('meterFill');

  let activeUrl = '';
  let activeVolume = 30;
  let mixEnabled = false;
  let audioContext = null;
  let gainNode = null;
  let bufferSource = null;
  let playbackMode = 'idle';

  function setStatus(text, detail) {
    statusEl.textContent = text || '';
    detailEl.textContent = detail || '';
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
    meterFill.style.width = `${activeVolume}%`;

    // IMPORTANT: do not call com.webos.audio/media/setVolume here. On the
    // retail B1 firmware that endpoint changes the TV/master path as well.
    // Web Audio GainNode is strictly local to this music stream.
    if (gainNode && audioContext) {
      gainNode.gain.setValueAtTime(activeVolume / 100, audioContext.currentTime);
    }
    player.volume = activeVolume / 100;
  }

  async function stopPlayers() {
    try { bufferSource && bufferSource.stop(0); } catch (_) {}
    try { bufferSource && bufferSource.disconnect(); } catch (_) {}
    bufferSource = null;
    try { gainNode && gainNode.disconnect(); } catch (_) {}
    gainNode = null;
    if (audioContext) {
      try { await audioContext.close(); } catch (_) {}
    }
    audioContext = null;

    try {
      player.pause();
      player.removeAttribute('src');
      player.load();
    } catch (_) {}
    playbackMode = 'idle';
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
      const ok = (buffer) => {
        if (completed) return;
        completed = true;
        resolve(buffer);
      };
      const fail = (error) => {
        if (completed) return;
        completed = true;
        reject(error || new Error('decodeAudioData fehlgeschlagen'));
      };
      try {
        const maybePromise = context.decodeAudioData(encoded.slice(0), ok, fail);
        if (maybePromise && typeof maybePromise.then === 'function') {
          maybePromise.then(ok, fail);
        }
      } catch (error) {
        fail(error);
      }
    });

    const gain = context.createGain();
    gain.gain.value = activeVolume / 100;
    gain.connect(context.destination);

    const source = context.createBufferSource();
    source.buffer = decoded;
    source.connect(gain);
    source.onended = () => {
      if (bufferSource === source) setStatus('Musik beendet', 'Stream vollständig abgespielt');
    };

    audioContext = context;
    gainNode = gain;
    bufferSource = source;
    playbackMode = 'webaudio';

    if (context.state === 'suspended') {
      try { await context.resume(); } catch (_) {}
    }
    source.start(0);
    setStatus('Web-Audio läuft', `${activeVolume}% · Hintergrund-Test bereit`);
  }

  async function startHtmlAudio(streamUrl) {
    playbackMode = 'htmlaudio';
    player.src = streamUrl;
    player.load();
    player.volume = activeVolume / 100;
    await player.play();
    setStatus('HTML-Audio läuft', `${activeVolume}% · Fallback`);
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
      // The B1 suspends normal HTML media when returning to TV/HDMI. Web Audio
      // is intentionally tested first because webOS does not automatically stop
      // Web Audio processing on app suspend.
      await startWebAudio(activeUrl);
    } catch (webAudioError) {
      try {
        await startHtmlAudio(activeUrl);
      } catch (htmlError) {
        playbackMode = 'idle';
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

    await startStream(command.streamUrl || activeUrl, command.volume);
  }

  function parseParams(value) {
    if (!value) return {};
    if (typeof value === 'object') return value;
    try {
      return JSON.parse(value);
    } catch (_) {
      return {};
    }
  }

  function initialParams() {
    if (window.webOSSystem && window.webOSSystem.launchParams) {
      return parseParams(window.webOSSystem.launchParams);
    }
    if (window.PalmSystem && window.PalmSystem.launchParams) {
      return parseParams(window.PalmSystem.launchParams);
    }
    return {};
  }

  player.addEventListener('playing', () => {
    if (playbackMode === 'htmlaudio') setStatus('HTML-Audio läuft', `${activeVolume}% · Fallback`);
  });

  player.addEventListener('waiting', () => {
    if (playbackMode === 'htmlaudio') setStatus('Puffert …', activeUrl);
  });

  player.addEventListener('error', () => {
    const code = player.error ? player.error.code : '?';
    if (playbackMode === 'htmlaudio') setStatus('Audiofehler', `MediaError ${code} · ${activeUrl}`);
  });

  document.addEventListener('visibilitychange', () => {
    if (document.hidden && activeUrl) {
      // Do not stop Web Audio here: this is the actual root-free persistence test.
      setMix(true);
    }
  });

  document.addEventListener('webOSRelaunch', (event) => {
    applyCommand(parseParams(event && event.detail));
  });

  window.addEventListener('beforeunload', () => {
    if (mixEnabled) {
      try {
        const bridge = new window.PalmServiceBridge();
        bridge.call(
          'luna://com.webos.service.audio/tv/mixDigitalSoundOutput',
          JSON.stringify({ mix: false }),
        );
      } catch (_) {}
    }
  });

  setStatus('Bridge startet …', 'SmartIR wartet auf Audio');
  applyCommand(initialParams());
})();
