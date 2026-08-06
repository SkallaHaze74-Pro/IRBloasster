(() => {
  'use strict';

  const urls = {
    library: 'https://soundcloud.com/you/library',
    login: 'https://soundcloud.com/login'
  };

  const status = document.getElementById('status');
  const libraryButton = document.getElementById('library');
  const loginButton = document.getElementById('login');
  const buttons = [libraryButton, loginButton];

  let selectedIndex = 0;
  let launching = false;
  let bridge = null;
  let autoLaunchTimer = null;

  function setButtonsDisabled(disabled) {
    buttons.forEach(button => {
      button.disabled = disabled;
    });
  }

  function fallbackToHostedPage(url) {
    status.textContent = 'LG-Browser konnte nicht gestartet werden – direkter Webmodus wird versucht …';
    window.location.replace(url);
  }

  function launchBrowser(url, label) {
    if (launching) return;

    launching = true;
    clearTimeout(autoLaunchTimer);
    setButtonsDisabled(true);
    status.textContent = `${label} wird im LG-Browser geöffnet …`;

    try {
      if (typeof PalmServiceBridge !== 'function') {
        fallbackToHostedPage(url);
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
          fallbackToHostedPage(url);
        }
      };

      bridge.call(
        'luna://com.webos.applicationManager/launch',
        JSON.stringify({
          id: 'com.webos.app.browser',
          params: {
            target: url
          }
        })
      );

      setTimeout(() => {
        if (!callbackReceived && !document.hidden) {
          fallbackToHostedPage(url);
        }
      }, 4000);
    } catch (_) {
      fallbackToHostedPage(url);
    }
  }

  function focusButton(index) {
    selectedIndex = (index + buttons.length) % buttons.length;
    buttons[selectedIndex].focus();
  }

  function resetLauncher() {
    launching = false;
    setButtonsDisabled(false);
    status.textContent = 'SoundCloud wird im LG-Browser geöffnet …';
    focusButton(0);
  }

  libraryButton.addEventListener('click', () => {
    launchBrowser(urls.library, 'Deine SoundCloud-Bibliothek');
  });

  loginButton.addEventListener('click', () => {
    launchBrowser(urls.login, 'Die SoundCloud-Anmeldung');
  });

  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) {
      setTimeout(resetLauncher, 250);
    }
  });

  document.addEventListener('keydown', event => {
    const keyCode = event.keyCode;
    const key = event.key;

    if (key === 'ArrowUp' || key === 'ArrowLeft' || keyCode === 38 || keyCode === 37) {
      clearTimeout(autoLaunchTimer);
      focusButton(selectedIndex - 1);
      event.preventDefault();
      return;
    }

    if (key === 'ArrowDown' || key === 'ArrowRight' || keyCode === 40 || keyCode === 39) {
      clearTimeout(autoLaunchTimer);
      focusButton(selectedIndex + 1);
      event.preventDefault();
      return;
    }

    if (key === 'Enter' || keyCode === 13) {
      clearTimeout(autoLaunchTimer);
      buttons[selectedIndex].click();
      event.preventDefault();
      return;
    }

    if (key === 'Escape' || key === 'Backspace' || keyCode === 461 || keyCode === 8) {
      window.close();
      event.preventDefault();
    }
  });

  buttons.forEach((button, index) => {
    button.addEventListener('focus', () => {
      selectedIndex = index;
      clearTimeout(autoLaunchTimer);
    });
  });

  focusButton(0);

  autoLaunchTimer = setTimeout(() => {
    launchBrowser(urls.library, 'Deine SoundCloud-Bibliothek');
  }, 1800);
})();
