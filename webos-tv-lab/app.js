(() => {
  'use strict';

  const patterns = [
    {
      id: 'hlg-video',
      title: 'HLG HDR · 4K Signaltest',
      subtitle: 'VP9 Profile 2 oder HEVC Main10 · BT.2020 · ARIB STD-B67',
      help: 'Der LG sollte „HLG HDR“ einblenden. OK startet oder pausiert. Links/Rechts wechselt VP9 4K, HEVC 4K und HEVC 1080p. Der Status zeigt Codec, Auflösung und Decoderzustand.',
      videoSources: [
        { label: 'VP9 Profile 2 · 4K', src: 'media/SmartIR-HLG-4K-VP9.webm', type: 'video/webm; codecs="vp09.02.10.10.01.09.18.09.00"' },
        { label: 'HEVC Main10 · 4K', src: 'media/SmartIR-HLG-4K-HEVC.mp4', type: 'video/mp4', mediaOption: true },
        { label: 'HEVC Main10 · 1080p', src: 'media/SmartIR-HLG-1080-HEVC.mp4', type: 'video/mp4', mediaOption: true }
      ],
      render(stage, sourceIndex = 0) {
        renderVideo(stage, this, sourceIndex);
      }
    },
    {
      id: 'hdr10-video',
      title: 'HDR10 · 4K Signaltest',
      subtitle: 'VP9 Profile 2 oder HEVC Main10 · BT.2020 · ST 2084/PQ',
      help: 'Der LG sollte „HDR“ einblenden. OK startet oder pausiert. Links/Rechts wechselt den Codec. HDR10 enthält Mastering-Daten sowie MaxCLL und MaxFALL.',
      videoSources: [
        { label: 'VP9 Profile 2 · 4K', src: 'media/SmartIR-HDR10-4K-VP9.webm', type: 'video/webm; codecs="vp09.02.10.10.01.09.16.09.00"' },
        { label: 'HEVC Main10 · 4K', src: 'media/SmartIR-HDR10-4K-HEVC.mp4', type: 'video/mp4', mediaOption: true },
        { label: 'HEVC Main10 · 1080p', src: 'media/SmartIR-HDR10-1080-HEVC.mp4', type: 'video/mp4', mediaOption: true }
      ],
      render(stage, sourceIndex = 0) {
        renderVideo(stage, this, sourceIndex);
      }
    },
    {
      id: 'sdr-video',
      title: '4K SDR Wiedergabetest',
      subtitle: 'H.264 · sichtbares Bewegungsmuster · Decoder-Gegenprobe',
      help: 'Dieser Test muss sichtbar laufen, löst aber kein HDR aus. Läuft SDR, während alle HDR-Codecs schwarz bleiben, liegt es am HDR-Codecpfad und nicht an der Fernbedienung.',
      videoSources: [
        { label: 'H.264 · 4K SDR', src: 'media/SmartIR-SDR-4K-H264.mp4', type: 'video/mp4', mediaOption: true },
        { label: 'H.264 · 1080p SDR', src: 'media/SmartIR-SDR-1080-H264.mp4', type: 'video/mp4', mediaOption: true }
      ],
      render(stage, sourceIndex = 0) {
        renderVideo(stage, this, sourceIndex);
      }
    },
    {
      id: 'pluge',
      title: 'Schwarzpegel · PLUGE',
      subtitle: 'Referenzschwarz und Near-Black',
      help: 'Die dunkelsten Balken sollen im Schwarz verschwinden, knapp hellere Balken aber unterscheidbar bleiben. Schwarzwert nur in kleinen Schritten ändern.',
      render(stage) {
        stage.className = 'pattern-stage pluge';
        [0, 2, 4, 8, 12, 16, 18, 20].forEach(level => {
          const bar = document.createElement('div');
          bar.className = 'bar';
          bar.style.background = `rgb(${level},${level},${level})`;
          bar.textContent = String(level);
          stage.appendChild(bar);
        });
      }
    },
    {
      id: 'near-black',
      title: 'Near-Black · 16–27',
      subtitle: 'OLED-Abstufungen direkt über Schwarz',
      help: 'Prüfe aus normaler Sitzposition, ob die Stufen gleichmäßig sichtbar werden. Nicht versuchen, jede Stufe maximal hell zu machen.',
      render(stage) {
        stage.className = 'pattern-stage near-black';
        for (let level = 16; level <= 27; level += 1) {
          const patch = document.createElement('div');
          patch.className = 'patch';
          patch.style.background = `rgb(${level},${level},${level})`;
          patch.textContent = String(level);
          stage.appendChild(patch);
        }
      }
    },
    {
      id: 'white',
      title: 'Weiß-Clipping',
      subtitle: 'Helle Abstufungen 230–255',
      help: 'Felder bis kurz vor 255 sollen möglichst getrennt erkennbar sein. Fallen viele zusammen, Kontrast prüfen – nicht blind verändern.',
      render(stage) {
        stage.className = 'pattern-stage white-clipping';
        [230, 234, 238, 242, 246, 248, 250, 252, 253, 254, 255, 244].forEach(level => {
          const patch = document.createElement('div');
          patch.className = 'white-patch';
          patch.style.background = `rgb(${level},${level},${level})`;
          patch.textContent = String(level);
          stage.appendChild(patch);
        });
      }
    },
    {
      id: 'gray',
      title: 'Graustufenrampe',
      subtitle: '0–100 % · Gleichmäßigkeit und Farbstich',
      help: 'Achte auf Farbstiche, Banding und abrupte Helligkeitssprünge. Eine Smartphone-Kamera eignet sich nur für relative Vergleiche.',
      render(stage) {
        stage.className = 'pattern-stage gray-ramp';
        for (let index = 0; index <= 10; index += 1) {
          const level = Math.round(index * 25.5);
          const step = document.createElement('div');
          step.className = 'gray-step';
          step.style.background = `rgb(${level},${level},${level})`;
          step.style.color = level > 140 ? '#111' : '#fff';
          step.textContent = `${index * 10}%`;
          stage.appendChild(step);
        }
      }
    },
    {
      id: 'banding',
      title: 'Banding · Verläufe',
      subtitle: 'Schwarz–Weiß und Farbübergänge',
      help: 'Die Verläufe sollen möglichst glatt sein. Harte Streifen können aus Quelle, Kompression, Bildverarbeitung oder Panelansteuerung stammen.',
      render(stage) {
        stage.className = 'pattern-stage banding';
        [
          'linear-gradient(90deg,#000,#fff)',
          'linear-gradient(90deg,#000,#00f,#0ff,#fff)',
          'linear-gradient(90deg,#000,#f00,#ff0,#fff)',
          'linear-gradient(90deg,#000,#0f0,#0ff,#fff)'
        ].forEach(background => {
          const row = document.createElement('div');
          row.style.background = background;
          stage.appendChild(row);
        });
      }
    },
    {
      id: 'colors',
      title: 'Farbbalken',
      subtitle: 'Primär- und Sekundärfarben',
      help: 'Grobe Sichtkontrolle. Farbraum und Weißabgleich nicht nach Geschmack im Service-Menü verändern.',
      render(stage) {
        stage.className = 'pattern-stage color-bars';
        ['#ffffff', '#ffff00', '#00ffff', '#00ff00', '#ff00ff', '#ff0000', '#0000ff']
          .forEach(color => {
            const bar = document.createElement('div');
            bar.style.background = color;
            stage.appendChild(bar);
          });
      }
    },
    {
      id: 'geometry',
      title: 'Geometrie & Overscan',
      subtitle: 'Pixelraster, Bildrand und Kreisform',
      help: 'Alle äußeren Linien sollen sichtbar sein. Bei HDMI/PC nach 1:1-Pixelabbildung beziehungsweise Just Scan suchen.',
      render(stage) {
        stage.className = 'pattern-stage geometry';
      }
    },
    {
      id: 'sharpness',
      title: 'Schärfe & 1-Pixel-Linien',
      subtitle: 'Halos, Doppelkonturen und Moiré',
      help: 'Linien sollen sauber bleiben. Helle Säume oder Doppelkonturen deuten auf zu hohe Schärfe oder Nachschärfung.',
      render(stage) {
        stage.className = 'pattern-stage sharpness';
      }
    },
    {
      id: 'motion',
      title: 'Bewegung & Judder',
      subtitle: 'Ruckeln, Doppelbilder und TruMotion vergleichen',
      help: 'Der helle Balken bewegt sich gleichmäßig. Verschiedene TruMotion-Einstellungen direkt miteinander vergleichen.',
      render(stage) {
        stage.className = 'pattern-stage motion-test';
        const track = document.createElement('div');
        const block = document.createElement('div');
        block.className = 'motion-block';
        track.appendChild(block);
        stage.appendChild(track);
      }
    },
    {
      id: 'neon',
      title: 'OLED Neon Sweep',
      subtitle: 'Satte Farben, Schwarzwert und Verlauf',
      help: 'Visueller OLED-Test ohne HDR-Umschaltung. Auf Banding, Tinting, Flächenrauschen und sauberes Schwarz achten.',
      render(stage) {
        stage.className = 'pattern-stage neon-sweep';
      }
    },
    {
      id: 'uniformity',
      title: 'OLED-Uniformität',
      subtitle: '1, 2, 5, 10, 25, 50 und 100 % Grau',
      help: 'Links/Rechts wechselt die Fläche. Dunkle Grauflächen nur kurz ansehen. Auf Banding, Tinting und ungleichmäßige Bereiche achten.',
      variants: ['#030303', '#050505', '#0d0d0d', '#1a1a1a', '#404040', '#808080', '#ffffff'],
      variantLabels: ['1 %', '2 %', '5 %', '10 %', '25 %', '50 %', '100 %'],
      render(stage, variantIndex = 0) {
        stage.className = 'pattern-stage solid';
        stage.style.background = this.variants[variantIndex];
      }
    },
    {
      id: 'solid',
      title: 'Vollflächen · Pixelcheck',
      subtitle: 'Schwarz, Weiß, Rot, Grün, Blau, Cyan, Magenta, Gelb',
      help: 'Links/Rechts wechselt die Farbe. Helle statische Vollflächen nur kurz verwenden.',
      variants: ['#000000', '#ffffff', '#ff0000', '#00ff00', '#0000ff', '#00ffff', '#ff00ff', '#ffff00'],
      variantLabels: ['Schwarz', 'Weiß', 'Rot', 'Grün', 'Blau', 'Cyan', 'Magenta', 'Gelb'],
      render(stage, variantIndex = 0) {
        stage.className = 'pattern-stage solid';
        stage.style.background = this.variants[variantIndex];
      }
    }
  ];

  const menu = document.getElementById('menu');
  const menuGrid = document.getElementById('menu-grid');
  const patternScreen = document.getElementById('pattern');
  const stage = document.getElementById('pattern-stage');
  const help = document.getElementById('pattern-help');

  let selectedIndex = 0;
  let activePattern = null;
  let activeVideo = null;
  let activeVideoSourceIndex = 0;
  let variantIndex = 0;
  let videoWatchdog = null;

  function mediaOptionType(baseType) {
    const options = { mediaTransportType: 'URI', option: { mediaFormat: { type: 'MP4' } } };
    return `${baseType};mediaOption=${escape(JSON.stringify(options))}`;
  }

  function videoErrorText(video) {
    if (!video || !video.error) return 'kein Browser-Fehlercode';
    const codes = {
      1: 'MEDIA_ERR_ABORTED',
      2: 'MEDIA_ERR_NETWORK',
      3: 'MEDIA_ERR_DECODE',
      4: 'MEDIA_ERR_SRC_NOT_SUPPORTED'
    };
    return codes[video.error.code] || `Fehler ${video.error.code}`;
  }

  function updateVideoStatus(prefix) {
    if (!activeVideo || !activePattern || !activePattern.videoSources) return;
    const source = activePattern.videoSources[activeVideoSourceIndex];
    const size = activeVideo.videoWidth > 0
      ? `${activeVideo.videoWidth}×${activeVideo.videoHeight}`
      : 'noch keine Bildgröße';
    const time = Number.isFinite(activeVideo.currentTime)
      ? activeVideo.currentTime.toFixed(1)
      : '0.0';
    help.textContent = `${activePattern.title} · ${source.label} · ${prefix} · ${size} · t=${time}s · ready=${activeVideo.readyState} · ${activePattern.help}`;
  }

  function requestVideoPlay() {
    if (!activeVideo) return;
    const playResult = activeVideo.play();
    if (playResult && typeof playResult.catch === 'function') {
      playResult.catch(error => {
        updateVideoStatus(`Start blockiert: ${error && error.name ? error.name : 'OK drücken'}`);
      });
    }
  }

  function renderVideo(target, pattern, sourceIndex) {
    target.className = 'pattern-stage video-stage';
    activeVideoSourceIndex = sourceIndex % pattern.videoSources.length;
    const sourceDef = pattern.videoSources[activeVideoSourceIndex];

    const video = document.createElement('video');
    video.className = 'signal-video';
    video.autoplay = true;
    video.loop = true;
    video.muted = true;
    video.preload = 'auto';
    video.controls = false;
    video.setAttribute('playsinline', '');
    video.setAttribute('webkit-playsinline', '');

    const source = document.createElement('source');
    source.src = sourceDef.src;
    source.type = sourceDef.mediaOption ? mediaOptionType(sourceDef.type) : sourceDef.type;
    video.appendChild(source);

    const badge = document.createElement('div');
    badge.className = 'video-badge';
    badge.textContent = `${pattern.title} · ${sourceDef.label}`;

    video.addEventListener('loadstart', () => updateVideoStatus('Datei wird geöffnet'));
    video.addEventListener('loadedmetadata', () => updateVideoStatus('Metadaten geladen'));
    video.addEventListener('loadeddata', () => updateVideoStatus('erstes Bild geladen'));
    video.addEventListener('canplay', () => {
      updateVideoStatus('bereit');
      requestVideoPlay();
    });
    video.addEventListener('playing', () => updateVideoStatus('Wiedergabe läuft'));
    video.addEventListener('pause', () => updateVideoStatus('pausiert'));
    video.addEventListener('waiting', () => updateVideoStatus('Decoder wartet'));
    video.addEventListener('stalled', () => updateVideoStatus('Datenfluss hängt'));
    video.addEventListener('error', () => updateVideoStatus(videoErrorText(video)));

    target.appendChild(video);
    target.appendChild(badge);
    activeVideo = video;
    video.load();

    clearTimeout(videoWatchdog);
    videoWatchdog = setTimeout(() => {
      if (!activeVideo || activeVideo !== video) return;
      if (video.readyState < 2 || video.videoWidth === 0) {
        updateVideoStatus(`kein Bild – ${videoErrorText(video)}; Links/Rechts anderen Codec wählen`);
      }
    }, 5000);

    setTimeout(requestVideoPlay, 250);
  }

  function cleanupActiveMedia() {
    clearTimeout(videoWatchdog);
    videoWatchdog = null;
    if (!activeVideo) return;
    activeVideo.pause();
    while (activeVideo.firstChild) activeVideo.removeChild(activeVideo.firstChild);
    activeVideo.removeAttribute('src');
    activeVideo.load();
    activeVideo = null;
  }

  function buildMenu() {
    menuGrid.replaceChildren();
    patterns.forEach((pattern, index) => {
      const card = document.createElement('article');
      card.className = `menu-card${index === selectedIndex ? ' selected' : ''}`;
      card.dataset.index = String(index);

      const title = document.createElement('h2');
      title.textContent = pattern.title;
      const subtitle = document.createElement('p');
      subtitle.textContent = pattern.subtitle;

      card.append(title, subtitle);
      card.addEventListener('click', () => {
        selectedIndex = index;
        openSelected();
      });
      menuGrid.appendChild(card);
    });
  }

  function updateSelection() {
    [...menuGrid.children].forEach((card, index) => {
      card.classList.toggle('selected', index === selectedIndex);
      if (index === selectedIndex && typeof card.scrollIntoView === 'function') {
        card.scrollIntoView({ block: 'nearest', inline: 'nearest' });
      }
    });
  }

  function openSelected() {
    activePattern = patterns[selectedIndex];
    variantIndex = 0;
    activeVideoSourceIndex = 0;
    menu.classList.add('hidden');
    patternScreen.classList.remove('hidden');
    renderActive();
  }

  function renderActive() {
    if (!activePattern) return;
    cleanupActiveMedia();
    stage.removeAttribute('style');
    stage.replaceChildren();
    activePattern.render(
      stage,
      activePattern.videoSources ? activeVideoSourceIndex : variantIndex
    );

    if (!activePattern.videoSources) {
      const variant = activePattern.variantLabels
        ? ` · ${activePattern.variantLabels[variantIndex]}`
        : '';
      help.textContent = `${activePattern.title}${variant} · ${activePattern.help}`;
    }
  }

  function showMenu() {
    cleanupActiveMedia();
    activePattern = null;
    stage.replaceChildren();
    patternScreen.classList.add('hidden');
    menu.classList.remove('hidden');
    updateSelection();
  }

  function moveSelection(delta) {
    selectedIndex = (selectedIndex + delta + patterns.length) % patterns.length;
    updateSelection();
  }

  function changeVariant(delta) {
    if (!activePattern) return;

    if (activePattern.videoSources) {
      activeVideoSourceIndex = (
        activeVideoSourceIndex + delta + activePattern.videoSources.length
      ) % activePattern.videoSources.length;
      renderActive();
      return;
    }

    if (!activePattern.variants) return;
    variantIndex = (
      variantIndex + delta + activePattern.variants.length
    ) % activePattern.variants.length;
    renderActive();
  }

  function toggleVideo() {
    if (!activeVideo) return;
    if (activeVideo.paused) requestVideoPlay();
    else activeVideo.pause();
  }

  document.addEventListener('keydown', event => {
    const key = event.key;
    const keyCode = event.keyCode;

    if (activePattern) {
      if (key === 'ArrowLeft' || keyCode === 37) changeVariant(-1);
      else if (key === 'ArrowRight' || keyCode === 39) changeVariant(1);
      else if (key === 'Enter' || keyCode === 13) toggleVideo();
      else if (key === 'Escape' || key === 'Backspace' || keyCode === 461 || keyCode === 8) showMenu();
      event.preventDefault();
      return;
    }

    if (key === 'ArrowLeft' || keyCode === 37) moveSelection(-1);
    else if (key === 'ArrowRight' || keyCode === 39) moveSelection(1);
    else if (key === 'ArrowUp' || keyCode === 38) moveSelection(-3);
    else if (key === 'ArrowDown' || keyCode === 40) moveSelection(3);
    else if (key === 'Enter' || keyCode === 13) openSelected();
    else if (key === 'Escape' || key === 'Backspace' || keyCode === 461 || keyCode === 8) window.close();
    event.preventDefault();
  });

  buildMenu();
})();
