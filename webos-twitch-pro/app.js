(() => {
  'use strict';

  const urls = {
    player: 'https://skallahaze74-pro.github.io/IRBloasster/',
    following: 'https://www.twitch.tv/directory/following',
    home: 'https://www.twitch.tv/'
  };

  const labels = {
    player: 'Der Twitch Source Player',
    following: 'Twitch Gefolgt und Login',
    home: 'Twitch Home'
  };

  const status = document.getElementById('status');
  const buttons = Array.from(document.querySelectorAll('.tile'));
  let selectedIndex = 0;
  let launching = false;
  let bridge = null;

  function setDisabled(disabled) {
    buttons.forEach(button => {
      button.disabled = disabled;
    });
  }

  function fallback(url) {
    status.textContent = 'LG-Browser konnte nicht gestartet werden · direkter Webmodus wird versucht …';
    window.location.replace(url);
  }

  function launch(key) {
    const url = urls[key];
    if (!url || launching) return;

    launching = true;
    setDisabled(true);
    status.textContent = `${labels[key]} wird im LG-Browser geöffnet …`;

    try {
      if (typeof PalmServiceBridge !== 'function') {
        fallback(url);
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

        if (response && response.returnValue === false) fallback(url);
      };

      bridge.call(
        'luna://com.webos.applicationManager/launch',
        JSON.stringify({
          id: 'com.webos.app.browser',
          params: { target: url }
        })
      );

      setTimeout(() => {
        if (!callbackReceived && !document.hidden) fallback(url);
      }, 4000);
    } catch (_) {
      fallback(url);
    }
  }

  function focusButton(index) {
    selectedIndex = (index + buttons.length) % buttons.length;
    buttons[selectedIndex].focus();
  }

  function resetLauncher() {
    launching = false;
    setDisabled(false);
    status.textContent = 'Source-Player, Login und Gefolgt im LG-Browser';
    focusButton(selectedIndex);
  }

  buttons.forEach((button, index) => {
    button.addEventListener('click', () => launch(button.dataset.target));
    button.addEventListener('focus', () => {
      selectedIndex = index;
    });
  });

  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) setTimeout(resetLauncher, 250);
  });

  document.addEventListener('keydown', event => {
    const key = event.key;
    const keyCode = event.keyCode;

    if (key === 'ArrowLeft' || key === 'ArrowUp' || keyCode === 37 || keyCode === 38) {
      focusButton(selectedIndex - 1);
      event.preventDefault();
    } else if (key === 'ArrowRight' || key === 'ArrowDown' || keyCode === 39 || keyCode === 40) {
      focusButton(selectedIndex + 1);
      event.preventDefault();
    } else if (key === 'Enter' || keyCode === 13) {
      buttons[selectedIndex].click();
      event.preventDefault();
    } else if (/^[1-3]$/.test(key)) {
      focusButton(Number(key) - 1);
      buttons[Number(key) - 1].click();
      event.preventDefault();
    } else if (key === 'Escape' || key === 'Backspace' || keyCode === 461 || keyCode === 8) {
      window.close();
      event.preventDefault();
    }
  });

  focusButton(0);
})();
