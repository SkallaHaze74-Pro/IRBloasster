(() => {
  'use strict';

  const player = document.getElementById('player');
  const statusEl = document.getElementById('status');
  const detailEl = document.getElementById('detail');
  const meterFill = document.getElementById('meterFill');

  let activeUrl = '';
  let activeVolume = 30;
  let mixEnabled = false;

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

  async function setMusicVolume(volume) {
    activeVolume = Math.max(0, Math.min(100, Number(volume) || 0));
    player.volume = activeVolume / 100;
    meterFill.style.width = `${activeVolume}%`;

    // LG keeps a dedicated media volume path for web/media audio. The HTML
    // element volume is kept as a second independent limiter in case this API
    // is not exposed on a particular firmware.
    try {
      await bridgeCall('luna://com.webos.audio/media/setVolume', { volume: activeVolume });
    } catch (_) {
      // player.volume remains an independent per-stream fallback.
    }
  }

  async function startStream(streamUrl, volume) {
    if (!streamUrl || !/^https?:\/\//i.test(streamUrl)) {
      setStatus('Keine gültige Audio-URL', streamUrl || 'streamUrl fehlt');
      return;
    }

    activeUrl = streamUrl;
    await setMusicVolume(volume == null ? activeVolume : volume);
    await setMix(true);

    if (player.src !== activeUrl) {
      player.src = activeUrl;
      player.load();
    }

    try {
      await player.play();
      setStatus('Musik läuft', `${activeVolume}% · ${activeUrl}`);
    } catch (error) {
      setStatus('Audio konnte nicht starten', String(error && error.message || error));
    }
  }

  async function stopStream() {
    try {
      player.pause();
      player.removeAttribute('src');
      player.load();
    } catch (_) {}
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
      await setMusicVolume(command.volume);
      if (mixEnabled) {
        setStatus('Musikpegel geändert', `${activeVolume}%`);
      }
      return;
    }

    if (action === 'ping') {
      setStatus('Bridge bereit', activeUrl ? `Stream aktiv · ${activeVolume}%` : 'Kein Stream aktiv');
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
    setStatus('Musik läuft', `${activeVolume}% · Root-free Test aktiv`);
  });

  player.addEventListener('waiting', () => {
    setStatus('Puffert …', activeUrl);
  });

  player.addEventListener('error', () => {
    const code = player.error ? player.error.code : '?';
    setStatus('Audiofehler', `MediaError ${code} · ${activeUrl}`);
  });

  document.addEventListener('visibilitychange', () => {
    if (document.hidden && activeUrl) {
      // Do not pause. Re-assert the mix flag before webOS suspends the page.
      setMix(true);
    }
  });

  document.addEventListener('webOSRelaunch', (event) => {
    // appinfo handlesRelaunch=true: volume/stop updates can be processed while
    // the app is backgrounded without calling PalmSystem.activate().
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
