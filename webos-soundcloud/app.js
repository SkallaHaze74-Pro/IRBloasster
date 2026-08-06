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

  const columnCount = 4;
  const status = document.getElementById('status');
  const resumeSubtitle = document.getElementById('resume-subtitle');
  const buttons = Array.from(document.querySelectorAll('.tile'));

  let selectedIndex = 0;
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

  function resolveButtonKey(button) {
    const key = button.dataset.key;
    return key === 'resume' ? storedLastKey() : key;
  }

  function setButtonsDisabled(disabled) {
    buttons.forEach(button => {
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
    setButtonsDisabled(true);
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

  function focusButton(index) {
    selectedIndex = (index + buttons.length) % buttons.length;
    buttons[selectedIndex].focus();
  }

  function moveHorizontal(delta) {
    const rowStart = Math.floor(selectedIndex / columnCount) * columnCount;
    const rowLength = Math.min(columnCount, buttons.length - rowStart);
    const position = selectedIndex - rowStart;
    focusButton(rowStart + (position + delta + rowLength) % rowLength);
  }

  function moveVertical(delta) {
    const target = selectedIndex + delta * columnCount;
    if (target >= 0 && target < buttons.length) {
      focusButton(target);
      return;
    }

    const column = selectedIndex % columnCount;
    if (delta > 0) {
      focusButton(Math.min(column, buttons.length - 1));
    } else {
      const lastRowStart = Math.floor((buttons.length - 1) / columnCount) * columnCount;
      focusButton(Math.min(lastRowStart + column, buttons.length - 1));
    }
  }

  function resetLauncher() {
    launching = false;
    setButtonsDisabled(false);
    status.textContent = 'Bereit · Anmeldung bleibt im LG-Browser gespeichert';
    updateResumeCard();
    focusButton(selectedIndex);
  }

  buttons.forEach((button, index) => {
    button.addEventListener('click', () => {
      launchBrowser(resolveButtonKey(button));
    });

    button.addEventListener('focus', () => {
      selectedIndex = index;
    });
  });

  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) {
      setTimeout(resetLauncher, 250);
    }
  });

  document.addEventListener('keydown', event => {
    const key = event.key;
    const keyCode = event.keyCode;

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
      buttons[selectedIndex].click();
      event.preventDefault();
      return;
    }

    const numeric = Number(key);
    if (Number.isInteger(numeric) && numeric >= 1 && numeric <= buttons.length) {
      focusButton(numeric - 1);
      buttons[numeric - 1].click();
      event.preventDefault();
      return;
    }

    if (key === 'Escape' || key === 'Backspace' || keyCode === 461 || keyCode === 8) {
      window.close();
      event.preventDefault();
    }
  });

  updateResumeCard();
  focusButton(0);
})();
