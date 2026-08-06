(() => {
  'use strict';

  const PARENT_DOMAIN = 'skallahaze74-pro.github.io';
  const LAST_CHANNEL_KEY = 'smartir.twitch.lastChannel';

  const setup = document.getElementById('setup');
  const playerScreen = document.getElementById('player-screen');
  const input = document.getElementById('channel');
  const startButton = document.getElementById('start');
  const resumeButton = document.getElementById('resume');
  const twitchSiteButton = document.getElementById('twitch-site');
  const playerHost = document.getElementById('twitch-player');
  const channelLabel = document.getElementById('channel-label');
  const status = document.getElementById('status');
  const qualityLabel = document.getElementById('quality');
  const setupControls = [input, startButton, resumeButton, twitchSiteButton];

  let setupIndex = 0;
  let player = null;
  let qualities = [];
  let qualityIndex = 0;
  let statsTimer = null;

  function savedChannel() {
    return (localStorage.getItem(LAST_CHANNEL_KEY) || '').trim();
  }

  function normalizeChannel(value) {
    return String(value || '')
      .trim()
      .replace(/^https?:\/\/(www\.)?twitch\.tv\//i, '')
      .split(/[/?#]/)[0]
      .toLowerCase();
  }

  function validChannel(channel) {
    return /^[a-z0-9_]{2,25}$/.test(channel);
  }

  function qualityRank(value) {
    if (value === 'chunked') return 100000;
    const match = String(value).match(/(\d+)p(\d+)?/i);
    if (!match) return 0;
    const resolution = Number(match[1]) || 0;
    const frameRate = Number(match[2]) || 30;
    return resolution * 100 + frameRate;
  }

  function qualityDisplay(value) {
    if (value === 'chunked') return 'Source';
    return value || 'Auto';
  }

  function sortedQualities(values) {
    return Array.from(new Set(values || []))
      .filter(Boolean)
      .sort((a, b) => qualityRank(b) - qualityRank(a));
  }

  function updateResumeButton() {
    const channel = savedChannel();
    resumeButton.disabled = !validChannel(channel);
    resumeButton.textContent = channel
      ? `Letzten Kanal starten · ${channel}`
      : 'Letzten Kanal starten';
  }

  function focusSetup(index) {
    setupIndex = (index + setupControls.length) % setupControls.length;
    setupControls[setupIndex].focus();
  }

  function showSetup() {
    clearInterval(statsTimer);
    statsTimer = null;

    if (player) {
      try {
        player.pause();
      } catch (_) {
        // Ignore player cleanup errors.
      }
    }

    player = null;
    qualities = [];
    playerHost.replaceChildren();
    playerScreen.classList.add('hidden');
    setup.classList.remove('hidden');
    updateResumeButton();
    input.value = savedChannel();
    focusSetup(input.value ? 1 : 0);
  }

  function refreshPlayerStatus() {
    if (!player) return;

    let currentQuality = '';
    try {
      currentQuality = player.getQuality();
    } catch (_) {
      currentQuality = '';
    }

    qualityLabel.textContent = qualityDisplay(currentQuality);
    status.textContent = currentQuality === 'chunked'
      ? 'Originale Senderqualität aktiv'
      : `Stream läuft · ${qualityDisplay(currentQuality)}`;
  }

  function chooseBestQuality() {
    if (!player) return;

    let available = [];
    try {
      available = player.getQualities() || [];
    } catch (_) {
      available = [];
    }

    qualities = sortedQualities(available);
    if (!qualities.length) {
      qualityLabel.textContent = 'Auto';
      status.textContent = 'Qualitätsliste noch nicht verfügbar';
      return;
    }

    qualityIndex = 0;
    try {
      player.setQuality(qualities[qualityIndex]);
    } catch (_) {
      status.textContent = 'Automatische Qualitätswahl wird vom Player verwaltet';
    }

    setTimeout(refreshPlayerStatus, 700);
  }

  function cycleQuality(delta) {
    if (!player || !qualities.length) return;
    qualityIndex = (qualityIndex + delta + qualities.length) % qualities.length;

    try {
      player.setQuality(qualities[qualityIndex]);
      qualityLabel.textContent = qualityDisplay(qualities[qualityIndex]);
      status.textContent = `Qualität wird auf ${qualityDisplay(qualities[qualityIndex])} gesetzt …`;
    } catch (_) {
      status.textContent = 'Qualitätswechsel wurde vom Player abgelehnt';
    }
  }

  function togglePlayback() {
    if (!player) return;

    try {
      if (player.isPaused()) player.play();
      else player.pause();
    } catch (_) {
      status.textContent = 'Play/Pause konnte nicht umgeschaltet werden';
    }
  }

  function startChannel(rawChannel) {
    const channel = normalizeChannel(rawChannel);
    if (!validChannel(channel)) {
      input.value = channel;
      input.focus();
      input.setCustomValidity('Bitte einen gültigen Twitch-Kanalnamen eingeben.');
      input.reportValidity();
      return;
    }

    input.setCustomValidity('');
    localStorage.setItem(LAST_CHANNEL_KEY, channel);
    channelLabel.textContent = channel;
    status.textContent = 'Offizieller Twitch-Player wird geladen …';
    qualityLabel.textContent = 'Auto';

    setup.classList.add('hidden');
    playerScreen.classList.remove('hidden');
    playerHost.replaceChildren();

    if (!window.Twitch || typeof window.Twitch.Player !== 'function') {
      status.textContent = 'Twitch Player API konnte nicht geladen werden';
      return;
    }

    player = new window.Twitch.Player('twitch-player', {
      width: '100%',
      height: '100%',
      channel,
      parent: [PARENT_DOMAIN],
      autoplay: true,
      muted: false
    });

    player.addEventListener(window.Twitch.Player.READY, () => {
      status.textContent = 'Player bereit · höchste Qualität wird gewählt …';
      chooseBestQuality();
      clearInterval(statsTimer);
      statsTimer = setInterval(refreshPlayerStatus, 2500);
    });

    player.addEventListener(window.Twitch.Player.PLAY, refreshPlayerStatus);
    player.addEventListener(window.Twitch.Player.PAUSE, () => {
      status.textContent = 'Pausiert';
    });
    player.addEventListener(window.Twitch.Player.OFFLINE, () => {
      status.textContent = 'Kanal ist derzeit offline';
    });

    if (window.Twitch.Player.PLAYBACK_BLOCKED) {
      player.addEventListener(window.Twitch.Player.PLAYBACK_BLOCKED, () => {
        status.textContent = 'Autoplay blockiert · OK drücken';
      });
    }
  }

  startButton.addEventListener('click', () => startChannel(input.value));
  resumeButton.addEventListener('click', () => startChannel(savedChannel()));
  twitchSiteButton.addEventListener('click', () => {
    window.location.assign('https://www.twitch.tv/directory/following');
  });

  input.addEventListener('input', () => {
    input.setCustomValidity('');
  });

  setupControls.forEach((control, index) => {
    control.addEventListener('focus', () => {
      setupIndex = index;
    });
  });

  document.addEventListener('keydown', event => {
    const key = event.key;
    const keyCode = event.keyCode;

    if (!playerScreen.classList.contains('hidden')) {
      if (key === 'Enter' || keyCode === 13) {
        togglePlayback();
        event.preventDefault();
      } else if (key === 'ArrowLeft' || keyCode === 37) {
        cycleQuality(-1);
        event.preventDefault();
      } else if (key === 'ArrowRight' || keyCode === 39) {
        cycleQuality(1);
        event.preventDefault();
      } else if (key === 'Escape' || key === 'Backspace' || keyCode === 461 || keyCode === 8) {
        showSetup();
        event.preventDefault();
      }
      return;
    }

    if (key === 'ArrowUp' || key === 'ArrowLeft' || keyCode === 38 || keyCode === 37) {
      focusSetup(setupIndex - 1);
      event.preventDefault();
    } else if (key === 'ArrowDown' || key === 'ArrowRight' || keyCode === 40 || keyCode === 39) {
      focusSetup(setupIndex + 1);
      event.preventDefault();
    } else if (key === 'Enter' || keyCode === 13) {
      if (document.activeElement === input) startChannel(input.value);
      else setupControls[setupIndex].click();
      event.preventDefault();
    }
  });

  input.value = savedChannel();
  updateResumeButton();
  focusSetup(input.value ? 1 : 0);
})();
