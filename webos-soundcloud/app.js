(() => {
  'use strict';

  const destinations = {
    library: {
      label: 'Bibliothek',
      url: 'https://soundcloud.com/you/library'
    },
    likes: {
      label: 'Likes',
      url: 'https://soundcloud.com/you/likes'
    },
    playlists: {
      label: 'Playlists',
      url: 'https://soundcloud.com/you/sets'
    },
    stream: {
      label: 'Stream',
      url: 'https://soundcloud.com/stream'
    },
    discover: {
      label: 'Entdecken',
      url: 'https://soundcloud.com/discover'
    },
    search: {
      label: 'Suche',
      url: 'https://soundcloud.com/search'
    },
    login: {
      label: 'Anmeldung',
      url: 'https://soundcloud.com/login'
    }
  };

  const systemApps = {
    soundShare: {
      id: 'com.webos.app.btspeakerapp',
      label: 'Bluetooth Sound Share'
    },
    homeConnect: {
      id: 'com.webos.app.homeconnect',
      label: 'Home Dashboard'
    }
  };

  const columnCount = 3;
  const status = document.getElementById('status');
  const resumeSubtitle = document.getElementById('resume-subtitle');
  const menuButtons = Array.from(document.querySelectorAll('.tile'));
  const soundShareLab = document.getElementById('soundshare-lab');
  const soundShareStatus = document.getElementById('soundshare-status');
  const soundShareButtons = [
    document.getElementById('open-soundshare'),
    document.getElementById('open-homeconnect'),
    document.getElementById('close-soundshare-lab')
  ];

  let selectedIndex = 0;
  let soundShareIndex = 0;
  let modalOpen = false;
  let launching = false;
  let bridge = null;

  function storedLastKey() {
    const value = localStorage.getItem('soundcloud.lastDestination');
    return destinations[value] && value !== 'login' ? value : 'library';
  }

  function updateResumeCard() {
    const lastKey = storedLastKey();
    const destination = destinations[lastKey];
    resumeSubtitle.textContent = `${destination.label} erneut öffnen`;
  }

  function setMenuDisabled(disabled) {
    menuButtons.forEach(button => {
      button.disabled = disabled;
    });
  }

  function setSoundShareDisabled(disabled) {
    soundShareButtons.forEach(button => {
      button.disabled = disabled;
    });
  }

  function fallbackToHostedPage(url) {
    status.textContent = 'LG-Browser konnte nicht gestartet werden · direkter Webmodus wird versucht …';
    window.location.replace(url);
  }

  function launchBrowser(key) {
    const destination = destinations[key];
    if (!destination || launching) return;

    launching = true;
    setMenuDisabled(true);
    status.textContent = `${destination.label} wird im LG-Browser geöffnet …`;

    if (key !== 'login') {
      localStorage.setItem('soundcloud.lastDestination', key);
      updateResumeCard();
    }

    try {
      if (typeof PalmServiceBridge !== 'function') {
        fallbackToHostedPage(destination.url);
        return;
      }

      bridge = new PalmServiceBridge();
      let callbackReceived = false;

      bridge.onservicecallback = responseText => {
        callbackReceived = true;
        let response = null;

        try {
          response = JSON.parse(responseText || '{}');
        } catch (_) {
          response = null;
        }

        if (response && response.returnValue === false) {
          fallbackToHostedPage(destination.url);
        }
      };

      bridge.call(
        'luna://com.webos.applicationManager/launch',
        JSON.stringify({
          id: 'com.webos.app.browser',
          params: {
            target: destination.url
          }
        })
      );

      setTimeout(() => {
        if (!callbackReceived && !document.hidden) {
          fallbackToHostedPage(destination.url);
        }
      }, 4000);
    } catch (_) {
      fallbackToHostedPage(destination.url);
    }
  }

  function launchSystemApp(systemApp) {
    if (!systemApp || launching) return;

    launching = true;
    setSoundShareDisabled(true);
    status.textContent = `${systemApp.label} wird geöffnet …`;
    soundShareStatus.textContent = `${systemApp.label} wird gestartet …`;

    try {
      if (typeof PalmServiceBridge !== 'function') {
        launching = false;
        setSoundShareDisabled(false);
        soundShareStatus.textContent = 'PalmServiceBridge fehlt. Öffne das Home Dashboard manuell über langes Drücken der Eingangstaste.';
        return;
      }

      bridge = new PalmServiceBridge();
      let callbackReceived = false;

      bridge.onservicecallback = responseText => {
        callbackReceived = true;
        let response = null;

        try {
          response = JSON.parse(responseText || '{}');
        } catch (_) {
          response = null;
        }

        if (response && response.returnValue === false) {
          launching = false;
          setSoundShareDisabled(false);
          soundShareStatus.textContent = `${systemApp.label} wurde abgelehnt: ${response.errorText || 'unbekannter Fehler'}. Nutze alternativ das Home Dashboard.`;
          return;
        }

        soundShareStatus.textContent = `${systemApp.label} wurde gestartet. Verbinde jetzt dein Xiaomi per Bluetooth mit dem LG TV und starte SoundCloud auf dem Handy.`;
      };

      bridge.call(
        'luna://com.webos.applicationManager/launch',
        JSON.stringify({
          id: systemApp.id,
          params: {}
        })
      );

      setTimeout(() => {
        if (!callbackReceived && !document.hidden) {
          launching = false;
          setSoundShareDisabled(false);
          soundShareStatus.textContent = `Kein sichtbarer Wechsel zu ${systemApp.label}. Öffne das Home Dashboard mit langem Drücken der Eingangstaste und wähle dort Sound Share im Bereich Mobil.`;
        }
      }, 4500);
    } catch (error) {
      launching = false;
      setSoundShareDisabled(false);
      soundShareStatus.textContent = `${systemApp.label} konnte nicht gestartet werden: ${error && error.message ? error.message : 'unbekannter Fehler'}`;
    }
  }

  function openSoundShareLab() {
    modalOpen = true;
    soundShareLab.classList.remove('hidden');
    soundShareStatus.textContent = 'Der vorige Systemplayer-Test ist fehlgeschlagen. Jetzt testen wir den eingebauten Bluetooth-Empfänger des LG TV.';
    soundShareIndex = 0;
    soundShareButtons[soundShareIndex].focus();
  }

  function closeSoundShareLab() {
    modalOpen = false;
    soundShareLab.classList.add('hidden');
    menuButtons[selectedIndex].focus();
  }

  function focusMenu(index) {
    selectedIndex = (index + menuButtons.length) % menuButtons.length;
    menuButtons[selectedIndex].focus();
  }

  function focusSoundShare(index) {
    soundShareIndex = (index + soundShareButtons.length) % soundShareButtons.length;
    soundShareButtons[soundShareIndex].focus();
  }

  function moveHorizontal(delta) {
    const rowStart = Math.floor(selectedIndex / columnCount) * columnCount;
    const rowLength = Math.min(columnCount, menuButtons.length - rowStart);
    const position = selectedIndex - rowStart;
    focusMenu(rowStart + (position + delta + rowLength) % rowLength);
  }

  function moveVertical(delta) {
    const target = selectedIndex + delta * columnCount;
    if (target >= 0 && target < menuButtons.length) {
      focusMenu(target);
      return;
    }

    const column = selectedIndex % columnCount;
    if (delta > 0) {
      focusMenu(Math.min(column, menuButtons.length - 1));
    } else {
      const lastRowStart = Math.floor((menuButtons.length - 1) / columnCount) * columnCount;
      focusMenu(Math.min(lastRowStart + column, menuButtons.length - 1));
    }
  }

  function resetLauncher() {
    launching = false;
    setMenuDisabled(false);
    setSoundShareDisabled(false);
    status.textContent = 'Bereit · Anmeldung bleibt im LG-Browser gespeichert';
    updateResumeCard();

    if (modalOpen) {
      soundShareStatus.textContent = 'Zurück von Sound Share. Starte SoundCloud auf dem Handy und teste anschließend den Wechsel auf HDMI oder Live-TV.';
      soundShareButtons[soundShareIndex].focus();
    } else {
      menuButtons[selectedIndex].focus();
    }
  }

  menuButtons.forEach((button, index) => {
    button.addEventListener('click', () => {
      const key = button.dataset.key;

      if (key === 'soundshare') {
        openSoundShareLab();
        return;
      }

      launchBrowser(key === 'resume' ? storedLastKey() : key);
    });

    button.addEventListener('focus', () => {
      selectedIndex = index;
    });
  });

  soundShareButtons.forEach((button, index) => {
    button.addEventListener('focus', () => {
      soundShareIndex = index;
    });
  });

  soundShareButtons[0].addEventListener('click', () => launchSystemApp(systemApps.soundShare));
  soundShareButtons[1].addEventListener('click', () => launchSystemApp(systemApps.homeConnect));
  soundShareButtons[2].addEventListener('click', closeSoundShareLab);

  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) {
      setTimeout(resetLauncher, 300);
    }
  });

  document.addEventListener('keydown', event => {
    const key = event.key;
    const keyCode = event.keyCode;

    if (modalOpen) {
      if (key === 'ArrowUp' || key === 'ArrowLeft' || keyCode === 38 || keyCode === 37) {
        focusSoundShare(soundShareIndex - 1);
        event.preventDefault();
      } else if (key === 'ArrowDown' || key === 'ArrowRight' || keyCode === 40 || keyCode === 39) {
        focusSoundShare(soundShareIndex + 1);
        event.preventDefault();
      } else if (key === 'Enter' || keyCode === 13) {
        soundShareButtons[soundShareIndex].click();
        event.preventDefault();
      } else if (key === 'Escape' || key === 'Backspace' || keyCode === 461 || keyCode === 8) {
        closeSoundShareLab();
        event.preventDefault();
      }
      return;
    }

    if (key === 'ArrowLeft' || keyCode === 37) {
      moveHorizontal(-1);
      event.preventDefault();
      return;
    }

    if (key === 'ArrowRight' || keyCode === 39) {
      moveHorizontal(1);
      event.preventDefault();
      return;
    }

    if (key === 'ArrowUp' || keyCode === 38) {
      moveVertical(-1);
      event.preventDefault();
      return;
    }

    if (key === 'ArrowDown' || keyCode === 40) {
      moveVertical(1);
      event.preventDefault();
      return;
    }

    if (key === 'Enter' || keyCode === 13) {
      menuButtons[selectedIndex].click();
      event.preventDefault();
      return;
    }

    const numeric = Number(key);
    if (Number.isInteger(numeric) && numeric >= 1 && numeric <= menuButtons.length) {
      focusMenu(numeric - 1);
      menuButtons[numeric - 1].click();
      event.preventDefault();
      return;
    }

    if (key === 'Escape' || key === 'Backspace' || keyCode === 461 || keyCode === 8) {
      window.close();
      event.preventDefault();
    }
  });

  updateResumeCard();
  focusMenu(0);
})();
