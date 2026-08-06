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

  const localTestSource = 'file:///media/developer/apps/usr/palm/applications/com.skallahaze.soundcloudtv/media/lg-background-test.wav';
  const httpsTestSource = 'https://media.w3.org/2010/07/bunny/04-Death_Becomes_Fur.oga';
  const columnCount = 3;

  const status = document.getElementById('status');
  const resumeSubtitle = document.getElementById('resume-subtitle');
  const menuButtons = Array.from(document.querySelectorAll('.tile'));
  const backgroundLab = document.getElementById('background-lab');
  const backgroundStatus = document.getElementById('background-status');
  const testAudio = document.getElementById('background-test-audio');
  const backgroundButtons = [
    document.getElementById('test-in-app'),
    document.getElementById('handoff-local'),
    document.getElementById('handoff-https'),
    document.getElementById('close-background-lab')
  ];

  let selectedIndex = 0;
  let backgroundIndex = 0;
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

  function buildMediaPayload(source, kind) {
    const isHttps = kind === 'https';
    return {
      id: 'com.webos.app.mediadiscovery',
      params: {
        payload: [{
          fullPath: source,
          artist: 'SmartIR',
          subtitle: '',
          dlnaInfo: {
            flagVal: 4096,
            cleartextSize: '-1',
            contentLength: '-1',
            opVal: 1,
            protocolInfo: isHttps
              ? 'http-get:*:audio/ogg:DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01500000000000000000000000000000'
              : 'file-get:*:audio/wav:*',
            duration: isHttps ? 0 : 45
          },
          mediaType: 'MUSIC',
          thumbnail: '',
          deviceType: 'DMR',
          album: 'SmartIR Tests',
          fileName: isHttps
            ? 'SmartIR HTTPS Hintergrundtest'
            : 'SmartIR lokaler Hintergrundtest',
          lastPlayPosition: 0
        }]
      }
    };
  }

  function launchLgMusicPlayer(source, kind) {
    if (launching) return;

    launching = true;
    backgroundButtons.forEach(button => {
      button.disabled = true;
    });

    const sourceLabel = kind === 'https' ? 'HTTPS-Fallback' : 'lokale WAV';
    backgroundStatus.textContent = `LG-Musikplayer wird mit ${sourceLabel} gestartet …`;
    status.textContent = `LG Hintergrundtest · ${sourceLabel}`;

    try {
      if (typeof PalmServiceBridge !== 'function') {
        launching = false;
        backgroundButtons.forEach(button => {
          button.disabled = false;
        });
        backgroundStatus.textContent = 'PalmServiceBridge fehlt. Der Systemplayer kann aus dieser Umgebung nicht gestartet werden.';
        return;
      }

      bridge = new PalmServiceBridge();
      bridge.onservicecallback = responseText => {
        let response = null;

        try {
          response = JSON.parse(responseText || '{}');
        } catch (_) {
          response = null;
        }

        if (response && response.returnValue === false) {
          launching = false;
          backgroundButtons.forEach(button => {
            button.disabled = false;
          });
          backgroundStatus.textContent = `LG-Player hat den Start abgelehnt: ${response.errorText || 'unbekannter Fehler'}`;
          return;
        }

        backgroundStatus.textContent = 'Startbefehl angenommen. Sobald Musik läuft: Eingangstaste drücken, HDMI oder Live-TV wählen und prüfen, ob der Ton weiterläuft.';
      };

      bridge.call(
        'luna://com.webos.applicationManager/launch',
        JSON.stringify(buildMediaPayload(source, kind))
      );

      setTimeout(() => {
        if (!document.hidden && launching) {
          launching = false;
          backgroundButtons.forEach(button => {
            button.disabled = false;
          });
          backgroundStatus.textContent = 'Kein sichtbarer Wechsel zum LG-Player. Probiere die andere Quelle oder sende mir ein Foto der Meldung.';
        }
      }, 5500);
    } catch (error) {
      launching = false;
      backgroundButtons.forEach(button => {
        button.disabled = false;
      });
      backgroundStatus.textContent = `LG-Player konnte nicht gestartet werden: ${error && error.message ? error.message : 'unbekannter Fehler'}`;
    }
  }

  function playAppTest() {
    testAudio.pause();
    testAudio.currentTime = 0;

    const playResult = testAudio.play();
    backgroundStatus.textContent = 'Der 45-Sekunden-Testton läuft jetzt in SoundCloud TV Pro. Wenn du ihn hörst, ist die Datei in Ordnung.';

    if (playResult && typeof playResult.catch === 'function') {
      playResult.catch(() => {
        backgroundStatus.textContent = 'Der Testton konnte in der App nicht gestartet werden. Drücke erneut OK oder prüfe die TV-Lautstärke.';
      });
    }
  }

  function openBackgroundLab() {
    modalOpen = true;
    backgroundLab.classList.remove('hidden');
    backgroundStatus.textContent = 'Zuerst den Testton in der App prüfen. Danach an den LG-Musikplayer übergeben und auf HDMI oder Live-TV wechseln.';
    backgroundIndex = 0;
    backgroundButtons[backgroundIndex].focus();
  }

  function closeBackgroundLab() {
    testAudio.pause();
    modalOpen = false;
    backgroundLab.classList.add('hidden');
    menuButtons[selectedIndex].focus();
  }

  function focusMenu(index) {
    selectedIndex = (index + menuButtons.length) % menuButtons.length;
    menuButtons[selectedIndex].focus();
  }

  function focusBackground(index) {
    backgroundIndex = (index + backgroundButtons.length) % backgroundButtons.length;
    backgroundButtons[backgroundIndex].focus();
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
    backgroundButtons.forEach(button => {
      button.disabled = false;
    });
    status.textContent = 'Bereit · Anmeldung bleibt im LG-Browser gespeichert';
    updateResumeCard();

    if (modalOpen) {
      backgroundStatus.textContent = 'Zurück vom LG-Player. Ist die Musik beim Wechsel zu HDMI oder Live-TV weitergelaufen?';
      backgroundButtons[backgroundIndex].focus();
    } else {
      menuButtons[selectedIndex].focus();
    }
  }

  menuButtons.forEach((button, index) => {
    button.addEventListener('click', () => {
      const key = button.dataset.key;

      if (key === 'background') {
        openBackgroundLab();
        return;
      }

      launchBrowser(key === 'resume' ? storedLastKey() : key);
    });

    button.addEventListener('focus', () => {
      selectedIndex = index;
    });
  });

  backgroundButtons.forEach((button, index) => {
    button.addEventListener('focus', () => {
      backgroundIndex = index;
    });
  });

  backgroundButtons[0].addEventListener('click', playAppTest);
  backgroundButtons[1].addEventListener('click', () => launchLgMusicPlayer(localTestSource, 'local'));
  backgroundButtons[2].addEventListener('click', () => launchLgMusicPlayer(httpsTestSource, 'https'));
  backgroundButtons[3].addEventListener('click', closeBackgroundLab);

  testAudio.addEventListener('ended', () => {
    backgroundStatus.textContent = 'App-Test beendet. Jetzt kannst du die lokale WAV an den LG-Musikplayer übergeben.';
  });

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
        focusBackground(backgroundIndex - 1);
        event.preventDefault();
      } else if (key === 'ArrowDown' || key === 'ArrowRight' || keyCode === 40 || keyCode === 39) {
        focusBackground(backgroundIndex + 1);
        event.preventDefault();
      } else if (key === 'Enter' || keyCode === 13) {
        backgroundButtons[backgroundIndex].click();
        event.preventDefault();
      } else if (key === 'Escape' || key === 'Backspace' || keyCode === 461 || keyCode === 8) {
        closeBackgroundLab();
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
