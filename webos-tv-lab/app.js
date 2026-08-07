(() => {
  'use strict';

  const patterns = [
    {
      id: 'pluge',
      title: 'Schwarzpegel · PLUGE',
      subtitle: 'Referenzschwarz und Near-Black',
      help: 'Die dunkelsten Balken sollen im Schwarz verschwinden, knapp hellere Balken aber noch unterscheidbar bleiben.',
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
      help: 'Prüfe aus normaler Sitzposition, ob die Stufen gleichmäßig sichtbar werden.',
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
      help: 'Felder bis kurz vor 255 sollen möglichst getrennt erkennbar sein.',
      render(stage) {
        stage.className = 'pattern-stage white-clipping';
        [230, 234, 238, 242, 244, 246, 248, 250, 252, 253, 254, 255].forEach(level => {
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
      subtitle: '0–100 % · Gleichmäßigkeit',
      help: 'Achte auf Farbstiche, Banding und abrupte Helligkeitssprünge.',
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
      id: 'colors',
      title: 'Farbbalken',
      subtitle: 'Primär- und Sekundärfarben',
      help: 'Schnelle Sichtkontrolle von Farbe und Kanälen.',
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
      id: 'uniformity',
      title: 'OLED-Uniformität',
      subtitle: '1, 2, 5, 10, 25, 50 und 100 % Grau',
      help: 'Links/Rechts wechselt die Fläche. Dunkle Grauflächen nur kurz ansehen.',
      variants: ['#030303', '#050505', '#0d0d0d', '#1a1a1a', '#404040', '#808080', '#ffffff'],
      labels: ['1 %', '2 %', '5 %', '10 %', '25 %', '50 %', '100 %'],
      render(stage, variantIndex) {
        stage.className = 'pattern-stage solid';
        stage.style.background = this.variants[variantIndex];
      }
    },
    {
      id: 'solid',
      title: 'Vollflächen · Pixelcheck',
      subtitle: 'Schwarz, Weiß, Rot, Grün, Blau',
      help: 'Links/Rechts wechselt die Farbe. Helle statische Vollflächen nur kurz verwenden.',
      variants: ['#000000', '#ffffff', '#ff0000', '#00ff00', '#0000ff'],
      labels: ['Schwarz', 'Weiß', 'Rot', 'Grün', 'Blau'],
      render(stage, variantIndex) {
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
  let activeIndex = -1;
  let variantIndex = 0;

  function renderMenu() {
    menuGrid.textContent = '';
    patterns.forEach((pattern, index) => {
      const card = document.createElement('div');
      card.className = `menu-card${index === selectedIndex ? ' selected' : ''}`;
      const title = document.createElement('h2');
      title.textContent = pattern.title;
      const subtitle = document.createElement('p');
      subtitle.textContent = pattern.subtitle;
      card.appendChild(title);
      card.appendChild(subtitle);
      menuGrid.appendChild(card);
    });
  }

  function clearStage() {
    stage.textContent = '';
    stage.removeAttribute('style');
    stage.className = 'pattern-stage';
  }

  function renderActive() {
    const pattern = patterns[activeIndex];
    if (!pattern) return;
    clearStage();
    const maxVariant = pattern.variants ? pattern.variants.length : 1;
    variantIndex = ((variantIndex % maxVariant) + maxVariant) % maxVariant;
    pattern.render(stage, variantIndex);
    const suffix = pattern.labels ? ` · ${pattern.labels[variantIndex]}` : '';
    help.textContent = `${pattern.title}${suffix} · ${pattern.help}`;
  }

  function openSelected() {
    activeIndex = selectedIndex;
    variantIndex = 0;
    menu.classList.add('hidden');
    patternScreen.classList.remove('hidden');
    renderActive();
  }

  function closePattern() {
    activeIndex = -1;
    clearStage();
    patternScreen.classList.add('hidden');
    menu.classList.remove('hidden');
    renderMenu();
  }

  function moveSelection(delta) {
    selectedIndex = (selectedIndex + delta + patterns.length) % patterns.length;
    renderMenu();
  }

  function onKey(event) {
    const key = event.key;
    const code = event.keyCode || event.which;

    if (activeIndex >= 0) {
      const pattern = patterns[activeIndex];
      if ((key === 'ArrowLeft' || code === 37) && pattern.variants) {
        variantIndex -= 1;
        renderActive();
        event.preventDefault();
        return;
      }
      if ((key === 'ArrowRight' || code === 39) && pattern.variants) {
        variantIndex += 1;
        renderActive();
        event.preventDefault();
        return;
      }
      if (key === 'Escape' || key === 'Backspace' || code === 461 || code === 27) {
        closePattern();
        event.preventDefault();
      }
      return;
    }

    if (key === 'ArrowUp' || code === 38) {
      moveSelection(-3);
      event.preventDefault();
    } else if (key === 'ArrowDown' || code === 40) {
      moveSelection(3);
      event.preventDefault();
    } else if (key === 'ArrowLeft' || code === 37) {
      moveSelection(-1);
      event.preventDefault();
    } else if (key === 'ArrowRight' || code === 39) {
      moveSelection(1);
      event.preventDefault();
    } else if (key === 'Enter' || code === 13) {
      openSelected();
      event.preventDefault();
    }
  }

  document.addEventListener('keydown', onKey, false);
  renderMenu();
})();
