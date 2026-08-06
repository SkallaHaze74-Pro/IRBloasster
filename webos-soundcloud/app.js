(() => {
  'use strict';

  const targetUrl = 'https://soundcloud.com/discover';
  const status = document.getElementById('status');
  const openButton = document.getElementById('open');
  let opening = false;

  function openSoundCloud() {
    if (opening) return;
    opening = true;
    status.textContent = 'SoundCloud wird geladen …';
    openButton.disabled = true;
    window.location.replace(targetUrl);
  }

  openButton.addEventListener('click', openSoundCloud);

  document.addEventListener('keydown', event => {
    const keyCode = event.keyCode;
    if (event.key === 'Enter' || keyCode === 13) {
      openSoundCloud();
      event.preventDefault();
    } else if (
      event.key === 'Escape' ||
      event.key === 'Backspace' ||
      keyCode === 461 ||
      keyCode === 8
    ) {
      window.close();
      event.preventDefault();
    }
  });

  setTimeout(openSoundCloud, 1400);
})();
