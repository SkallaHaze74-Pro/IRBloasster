(() => {
  'use strict';

  const patterns = [
    {
      id: 'pluge',
      title: 'Schwarzpegel · PLUGE',
      subtitle: 'Near-Black und Referenzschwarz prüfen',
      help: 'Die dunkelsten Balken sollen im Schwarz verschwinden, die knapp helleren Balken aber unterscheidbar bleiben. Helligkeit/Schwarzwert nur in kleinen Schritten ändern.',
      render(stage) {
        stage.className = 'pattern-stage pluge';
        [0, 2, 4, 16, 18, 20].forEach(level => {
          const bar = document.createElement('div');
          bar.className = 'bar';
          bar.style.background = `rgb(${level},${level},${level})`;
          stage.appendChild(bar);
        });
      }
    },
    {
      id: 'near-black',
      title: 'Near-Black-Stufen',
      subtitle: 'OLED-Abstufungen direkt über Schwarz',
      help: 'Prüfe aus normaler Sitzposition, ob die Stufen 16 bis 23 gleichmäßig sichtbar werden. Nicht versuchen, jede Stufe maximal hell zu machen.',
      render(stage) {
        stage.className = 'pattern-stage near-black';
        for (let level = 16; level <= 23; level += 1) {
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
      help: 'Alle Felder bis kurz vor 255 sollen möglichst getrennt erkennbar sein. Wenn viele Felder zusammenfallen, Kontrast und Tonemapping prüfen – nicht blind verändern.',
      render(stage) {
        stage.className = 'pattern-stage white-clipping';
        [230, 234, 238, 242, 246, 250, 252, 253, 254, 255, 248, 244].forEach(level => {
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
      subtitle: '0–100 % gleichmäßig und ohne Farbstich',
      help: 'Achte auf Farbstiche, Banding und abrupte Helligkeitssprünge. Eine Smartphone-Kamera kann Vergleiche dokumentieren, ersetzt aber kein Colorimeter.',
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
      subtitle: 'Primär- und Sekundärfarben vergleichen',
      help: 'Dieses Bild dient zur groben Sichtkontrolle und für spätere Kamera-/Colorimeter-Messungen. Farbraum und Weißabgleich nicht nach Geschmack in Service-Menüs ändern.',
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
      help: 'Alle äußeren Linien sollen sichtbar sein. Bei HDMI/PC-Eingängen nach 1:1-Pixelabbildung beziehungsweise Just Scan suchen.',
      render(stage) {
        stage.className = 'pattern-stage geometry';
      }
    },
    {
      id: 'solid',
      title: 'Vollflächen',
      subtitle: 'Schwarz, Weiß, Rot, Grün und Blau',
      help: 'Mit Links/Rechts zwischen Vollflächen wechseln. Nur kurz verwenden; statische helle Flächen nicht unnötig lange auf dem OLED stehen lassen.',
      variants: ['#000000', '#ffffff', '#ff0000', '#00ff00', '#0000ff'],
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
  let variantIndex = 0;

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
    });
  }

  function openSelected() {
    activePattern = patterns[selectedIndex];
    variantIndex = 0;
    menu.classList.add('hidden');
    patternScreen.classList.remove('hidden');
    renderActive();
  }

  function renderActive() {
    if (!activePattern) return;
    stage.removeAttribute('style');
    stage.replaceChildren();
    activePattern.render(stage, variantIndex);
    help.textContent = `${activePattern.title} · ${activePattern.help}`;
  }

  function showMenu() {
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
    if (!activePattern || !activePattern.variants) return;
    variantIndex = (
      variantIndex + delta + activePattern.variants.length
    ) % activePattern.variants.length;
    renderActive();
  }

  document.addEventListener('keydown', event => {
    const key = event.key;
    const keyCode = event.keyCode;

    if (activePattern) {
      if (key === 'ArrowLeft') changeVariant(-1);
      else if (key === 'ArrowRight') changeVariant(1);
      else if (key === 'Escape' || key === 'Backspace' || keyCode === 461) showMenu();
      event.preventDefault();
      return;
    }

    if (key === 'ArrowLeft') moveSelection(-1);
    else if (key === 'ArrowRight') moveSelection(1);
    else if (key === 'ArrowUp') moveSelection(-3);
    else if (key === 'ArrowDown') moveSelection(3);
    else if (key === 'Enter') openSelected();
    else if (key === 'Escape' || key === 'Backspace' || keyCode === 461) window.close();
    event.preventDefault();
  });

  buildMenu();
})();
