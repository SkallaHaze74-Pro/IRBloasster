(() => {
  'use strict';

  const root = document.documentElement;
  const hud = document.getElementById('hud');
  const syncValue = document.getElementById('syncValue');
  const canvas = document.getElementById('fxCanvas');
  const context = canvas && canvas.getContext ? canvas.getContext('2d') : null;
  const orbitShell = document.getElementById('orbitShell');

  const FPS = 30;
  const FRAME_MS = 1000 / FPS;
  const PARTICLE_COUNT = 150;
  const RADIAL_BARS = 96;
  const FLOOR_BARS = 64;
  const HISTORY_LIMIT = 12;

  let latestAnalyser = null;
  let frequencyData = null;
  let timeDomainData = null;
  let visualDelayMs = 35;
  let intensity = 1;
  let hudVisible = true;
  let width = 1920;
  let height = 1080;
  let dpr = 1;
  let lastFrameAt = 0;
  let lastCaptureAt = 0;
  let bassAverage = 0;
  let huePhase = 128;
  let history = [];
  let particles = [];
  let audioPatched = false;

  function clamp(value, min, max) {
    return Math.max(min, Math.min(max, value));
  }

  function averageRange(values, start, end) {
    if (!values || !values.length) return 0;
    const safeStart = Math.max(0, Math.min(values.length - 1, start));
    const safeEnd = Math.max(safeStart, Math.min(values.length - 1, end));
    let sum = 0;
    let count = 0;
    for (let index = safeStart; index <= safeEnd; index += 1) {
      sum += values[index] || 0;
      count += 1;
    }
    return count ? sum / count : 0;
  }

  function patchConstructor(Constructor) {
    if (!Constructor || !Constructor.prototype || Constructor.prototype.__smartIrVisualPatched) return false;
    const originalCreateAnalyser = Constructor.prototype.createAnalyser;
    if (typeof originalCreateAnalyser !== 'function') return false;

    Constructor.prototype.createAnalyser = function () {
      const analyser = originalCreateAnalyser.apply(this, arguments);
      latestAnalyser = analyser;
      frequencyData = new Uint8Array(analyser.frequencyBinCount);
      timeDomainData = new Uint8Array(analyser.fftSize);
      history = [];
      return analyser;
    };

    Constructor.prototype.__smartIrVisualPatched = true;
    return true;
  }

  function patchAudioApi() {
    const patchedA = patchConstructor(window.AudioContext);
    const patchedB = patchConstructor(window.webkitAudioContext);
    audioPatched = audioPatched || patchedA || patchedB;
    return audioPatched;
  }

  function updateLabel() {
    if (!syncValue) return;
    syncValue.textContent = `SYNC +${visualDelayMs} ms · FX ${Math.round(intensity * 100)}%`;
  }

  function setDelay(value) {
    visualDelayMs = Math.round(clamp(Number(value) || 0, 0, 180) / 5) * 5;
    updateLabel();
  }

  function setIntensity(value) {
    intensity = clamp(Number(value) || 1, 0.45, 1.8);
    root.style.setProperty('--intensity', intensity.toFixed(2));
    updateLabel();
  }

  function toggleHud() {
    hudVisible = !hudVisible;
    if (hud) hud.classList.toggle('hidden', !hudVisible);
  }

  function resize() {
    if (!canvas || !context) return;
    width = Math.max(1, window.innerWidth || 1920);
    height = Math.max(1, window.innerHeight || 1080);
    dpr = Math.min(1.35, Math.max(1, window.devicePixelRatio || 1));
    canvas.width = Math.round(width * dpr);
    canvas.height = Math.round(height * dpr);
    canvas.style.width = `${width}px`;
    canvas.style.height = `${height}px`;
    context.setTransform(dpr, 0, 0, dpr, 0, 0);
    createParticles();
  }

  function createParticles() {
    particles = [];
    const maxRadius = Math.sqrt(width * width + height * height) * 0.58;
    for (let index = 0; index < PARTICLE_COUNT; index += 1) {
      particles.push({
        angle: Math.random() * Math.PI * 2,
        radius: Math.random() * maxRadius,
        speed: 0.12 + Math.random() * 0.62,
        size: 0.7 + Math.random() * 2.6,
        alpha: 0.18 + Math.random() * 0.68,
        hue: Math.random() * 240,
        phase: Math.random() * Math.PI * 2,
      });
    }
  }

  function sampleLogBands(values, count, minBin, maxBin) {
    const bands = new Float32Array(count);
    if (!values || !values.length) return bands;
    const safeMin = Math.max(1, minBin);
    const safeMax = Math.max(safeMin + 1, Math.min(values.length - 1, maxBin));
    const ratio = safeMax / safeMin;

    for (let index = 0; index < count; index += 1) {
      const start = Math.max(safeMin, Math.floor(safeMin * Math.pow(ratio, index / count)));
      const end = Math.min(
        safeMax,
        Math.max(start, Math.floor(safeMin * Math.pow(ratio, (index + 1) / count))),
      );
      let peak = 0;
      let sum = 0;
      let samples = 0;
      for (let bin = start; bin <= end; bin += 1) {
        const value = values[bin] || 0;
        peak = Math.max(peak, value);
        sum += value;
        samples += 1;
      }
      const average = samples ? sum / samples : peak;
      bands[index] = clamp((peak * 0.66 + average * 0.34) / 255, 0, 1);
    }
    return bands;
  }

  function sampleWave(values, count) {
    const wave = new Float32Array(count);
    if (!values || !values.length) return wave;
    const stride = Math.max(1, Math.floor(values.length / count));
    for (let index = 0; index < count; index += 1) {
      wave[index] = ((values[Math.min(values.length - 1, index * stride)] || 128) - 128) / 128;
    }
    return wave;
  }

  function analyserIsActive() {
    if (!latestAnalyser) return false;
    try {
      return latestAnalyser.context && latestAnalyser.context.state !== 'closed';
    } catch (_) {
      return false;
    }
  }

  function capture(now) {
    let bass = 0;
    let mid = 0;
    let treble = 0;
    let energy = 0;
    let beat = 0;
    let bands = new Float32Array(FLOOR_BARS);
    let wave = new Float32Array(128);

    if (analyserIsActive()) {
      if (!frequencyData || frequencyData.length !== latestAnalyser.frequencyBinCount) {
        frequencyData = new Uint8Array(latestAnalyser.frequencyBinCount);
      }
      if (!timeDomainData || timeDomainData.length !== latestAnalyser.fftSize) {
        timeDomainData = new Uint8Array(latestAnalyser.fftSize);
      }

      latestAnalyser.getByteFrequencyData(frequencyData);
      latestAnalyser.getByteTimeDomainData(timeDomainData);

      const rawBass = averageRange(frequencyData, 1, 7) / 205;
      const rawMid = averageRange(frequencyData, 8, 52) / 190;
      const rawTreble = averageRange(frequencyData, 53, 185) / 175;

      bass = Math.pow(clamp(rawBass, 0, 1), 0.72);
      mid = Math.pow(clamp(rawMid, 0, 1), 0.76);
      treble = Math.pow(clamp(rawTreble, 0, 1), 0.80);

      bassAverage = bassAverage * 0.94 + bass * 0.06;
      const transient = Math.max(0, bass - bassAverage);
      beat = clamp(transient * 5.4, 0, 1);
      bass = clamp((bass * 0.72 + beat * 0.72) * intensity, 0, 1);
      mid = clamp(mid * intensity, 0, 1);
      treble = clamp(treble * intensity, 0, 1);
      energy = clamp(bass * 0.46 + mid * 0.34 + treble * 0.20, 0, 1);

      bands = sampleLogBands(frequencyData, FLOOR_BARS, 1, 210);
      wave = sampleWave(timeDomainData, 128);
    } else {
      const idle = now / 1000;
      bass = 0.035 + (Math.sin(idle * 1.7) + 1) * 0.012;
      mid = 0.025 + (Math.sin(idle * 1.13 + 1.2) + 1) * 0.010;
      treble = 0.020 + (Math.sin(idle * 2.4 + 2.1) + 1) * 0.008;
      energy = 0.035;
      for (let index = 0; index < bands.length; index += 1) {
        bands[index] = 0.015 + (Math.sin(idle * 1.2 + index * 0.31) + 1) * 0.012;
      }
      for (let index = 0; index < wave.length; index += 1) {
        wave[index] = Math.sin(idle * 1.3 + index * 0.14) * 0.04;
      }
    }

    huePhase = (huePhase + 0.18 + mid * 1.4 + treble * 2.2 + beat * 4.5) % 360;
    const hue = (118 + huePhase * 0.34 + mid * 92 + treble * 148) % 360;

    history.push({ time: now, bass, mid, treble, energy, beat, hue, bands, wave });
    while (history.length > HISTORY_LIMIT) history.shift();
  }

  function delayedFrame(now) {
    if (!history.length) return null;
    const target = now - visualDelayMs;
    let selected = history[0];
    for (let index = 0; index < history.length; index += 1) {
      if (history[index].time <= target) selected = history[index];
      else break;
    }
    while (history.length > 2 && history[1].time < target - 80) history.shift();
    return selected;
  }

  function applyCss(frame, now) {
    const hue2 = (frame.hue + 82 + frame.treble * 90) % 360;
    root.style.setProperty('--accent-hue', frame.hue.toFixed(1));
    root.style.setProperty('--accent-hue-2', hue2.toFixed(1));
    root.style.setProperty('--energy', frame.energy.toFixed(3));
    root.style.setProperty('--bass', frame.bass.toFixed(3));
    root.style.setProperty('--mid', frame.mid.toFixed(3));
    root.style.setProperty('--treble', frame.treble.toFixed(3));
    root.style.setProperty('--stage-alpha', (0.10 + frame.energy * 0.20).toFixed(3));
    root.style.setProperty('--stage-scale', (1 + frame.bass * 0.018).toFixed(3));
    root.style.setProperty('--ambient-opacity', (0.12 + frame.energy * 0.22).toFixed(3));
    root.style.setProperty('--ambient-a-x', `${(frame.mid * 30).toFixed(1)}px`);
    root.style.setProperty('--ambient-a-y', `${(frame.bass * -26).toFixed(1)}px`);
    root.style.setProperty('--ambient-b-x', `${(frame.treble * -30).toFixed(1)}px`);
    root.style.setProperty('--ambient-b-y', `${(frame.mid * 22).toFixed(1)}px`);
    root.style.setProperty('--ambient-c-scale', (1 + frame.bass * 0.18).toFixed(3));
    root.style.setProperty('--halo-scale', (1 + frame.bass * 0.075).toFixed(3));
    root.style.setProperty('--halo-opacity', (0.58 + frame.energy * 0.34).toFixed(3));
    root.style.setProperty('--leaf-scale', (0.92 + frame.bass * 0.085).toFixed(3));
    root.style.setProperty('--leaf-hue', `${(frame.hue - 130).toFixed(1)}deg`);
    root.style.setProperty('--leaf-saturate', (1.22 + frame.mid * 0.90).toFixed(3));
    root.style.setProperty('--leaf-brightness', (1 + frame.energy * 0.42).toFixed(3));
    root.style.setProperty('--aura-alpha', (0.14 + frame.bass * 0.24).toFixed(3));
    root.style.setProperty('--aura-scale', (1 + frame.bass * 0.16).toFixed(3));

    if (orbitShell) {
      const rotation = (now * 0.004 + frame.mid * 15) % 360;
      const scale = 1 + frame.beat * 0.024 + frame.energy * 0.010;
      orbitShell.style.transform =
        `translate(-50%, -50%) rotate(${rotation.toFixed(2)}deg) scale(${scale.toFixed(3)})`;
    }
  }

  function hsla(hue, saturation, lightness, alpha) {
    return `hsla(${Math.round((hue + 360) % 360)}, ${saturation}%, ${lightness}%, ${alpha})`;
  }

  function drawParticles(frame, now, centerX, centerY, maxRadius) {
    const speedBoost = 0.4 + frame.energy * 2.8 + frame.beat * 2.2;
    for (let index = 0; index < particles.length; index += 1) {
      const particle = particles[index];
      particle.radius += particle.speed * speedBoost;
      particle.angle += 0.0006 * (index % 2 ? 1 : -1) * (1 + frame.mid);
      if (particle.radius > maxRadius) {
        particle.radius = 4 + Math.random() * 18;
        particle.angle = Math.random() * Math.PI * 2;
      }

      const wobble = Math.sin(now * 0.0018 + particle.phase) * (4 + frame.treble * 10);
      const x = centerX + Math.cos(particle.angle) * (particle.radius + wobble);
      const y = centerY + Math.sin(particle.angle) * (particle.radius * 0.68 + wobble * 0.35);
      const twinkle = 0.54 + (Math.sin(now * 0.004 + particle.phase) + 1) * 0.23;
      const alpha = particle.alpha * twinkle * (0.36 + frame.energy * 0.72);
      const hue = frame.hue + particle.hue + frame.treble * 90;
      const size = particle.size * (0.75 + frame.energy * 0.85);

      context.beginPath();
      context.fillStyle = hsla(hue, 100, 68, alpha);
      context.shadowBlur = 7 + frame.energy * 15;
      context.shadowColor = hsla(hue, 100, 60, alpha);
      context.arc(x, y, size, 0, Math.PI * 2);
      context.fill();

      if (frame.energy > 0.34 && index % 7 === 0) {
        const trail = 5 + frame.energy * 16;
        context.beginPath();
        context.strokeStyle = hsla(hue, 100, 64, alpha * 0.38);
        context.lineWidth = Math.max(0.6, size * 0.52);
        context.moveTo(x, y);
        context.lineTo(
          x - Math.cos(particle.angle) * trail,
          y - Math.sin(particle.angle) * trail * 0.68,
        );
        context.stroke();
      }
    }
    context.shadowBlur = 0;
  }

  function drawRadial(frame, centerX, centerY, radius) {
    for (let index = 0; index < RADIAL_BARS; index += 1) {
      const mirroredIndex = index < RADIAL_BARS / 2 ? index : RADIAL_BARS - 1 - index;
      const sourceIndex = Math.floor((mirroredIndex / (RADIAL_BARS / 2)) * frame.bands.length);
      const value = Math.pow(
        clamp(frame.bands[Math.min(frame.bands.length - 1, sourceIndex)] || 0, 0, 1),
        0.70,
      );
      const angle = (index / RADIAL_BARS) * Math.PI * 2 - Math.PI / 2;
      const inner = radius * (1.03 + frame.bass * 0.016);
      const length = 8 + value * (42 + frame.energy * 74);
      const outer = inner + length;
      const hue = frame.hue + index * 2.55 + frame.treble * 95;
      const alpha = 0.24 + value * 0.76;

      context.beginPath();
      context.strokeStyle = hsla(hue, 100, 64, alpha);
      context.lineWidth = 1.2 + value * 4.2;
      context.lineCap = 'round';
      context.shadowBlur = 5 + value * 15;
      context.shadowColor = hsla(hue, 100, 59, alpha);
      context.moveTo(centerX + Math.cos(angle) * inner, centerY + Math.sin(angle) * inner);
      context.lineTo(centerX + Math.cos(angle) * outer, centerY + Math.sin(angle) * outer);
      context.stroke();
    }
    context.shadowBlur = 0;
  }

  function drawOrbits(frame, now, centerX, centerY, radius) {
    const rotations = [0.00022, -0.00015, 0.00010];
    const offsets = [0, 22, 46];

    for (let orbitIndex = 0; orbitIndex < rotations.length; orbitIndex += 1) {
      const orbitRadius = radius + offsets[orbitIndex];
      const spin = now * rotations[orbitIndex] + frame.mid * 0.8;
      const hue = frame.hue + orbitIndex * 72;
      const segments = 5 + orbitIndex * 2;

      context.lineWidth = 1.1 + frame.energy * 1.8;
      context.shadowBlur = 9 + frame.energy * 18;
      context.shadowColor = hsla(hue, 100, 60, 0.55);

      for (let segment = 0; segment < segments; segment += 1) {
        const start = spin + (segment / segments) * Math.PI * 2;
        const length = 0.16 + frame.energy * 0.08 + orbitIndex * 0.015;
        context.beginPath();
        context.strokeStyle = hsla(
          hue + segment * 18,
          100,
          70,
          0.22 + frame.energy * 0.34,
        );
        context.arc(centerX, centerY, orbitRadius, start, start + length);
        context.stroke();
      }
    }
    context.shadowBlur = 0;
  }

  function drawWave(frame, centerY) {
    const left = width * 0.10;
    const right = width * 0.90;
    const lineWidth = right - left;
    const amplitude = 18 + frame.energy * 55;
    const hue = frame.hue + 52;

    context.beginPath();
    for (let index = 0; index < frame.wave.length; index += 1) {
      const x = left + (index / (frame.wave.length - 1)) * lineWidth;
      const y = centerY + frame.wave[index] * amplitude;
      if (index === 0) context.moveTo(x, y);
      else context.lineTo(x, y);
    }
    context.lineWidth = 1.5 + frame.energy * 2.5;
    context.strokeStyle = hsla(hue, 100, 72, 0.38 + frame.energy * 0.46);
    context.shadowBlur = 12 + frame.energy * 22;
    context.shadowColor = hsla(hue, 100, 60, 0.72);
    context.stroke();

    context.beginPath();
    for (let index = 0; index < frame.wave.length; index += 1) {
      const x = right - (index / (frame.wave.length - 1)) * lineWidth;
      const y = centerY - frame.wave[index] * amplitude * 0.62;
      if (index === 0) context.moveTo(x, y);
      else context.lineTo(x, y);
    }
    context.lineWidth = 1;
    context.strokeStyle = hsla(hue + 115, 100, 70, 0.18 + frame.energy * 0.30);
    context.stroke();
    context.shadowBlur = 0;
  }

  function drawFloor(frame) {
    const left = width * 0.075;
    const right = width * 0.925;
    const baseY = height * 0.785;
    const totalWidth = right - left;
    const gap = Math.max(2, totalWidth * 0.0018);
    const barWidth = (totalWidth - gap * (FLOOR_BARS - 1)) / FLOOR_BARS;

    for (let index = 0; index < FLOOR_BARS; index += 1) {
      const value = Math.pow(clamp(frame.bands[index] || 0, 0, 1), 0.68);
      const barHeight = 4 + value * (44 + frame.energy * 84);
      const x = left + index * (barWidth + gap);
      const hue = frame.hue + index * 3.6;
      const gradient = context.createLinearGradient(0, baseY - barHeight, 0, baseY);
      gradient.addColorStop(0, hsla(hue + 18, 100, 76, 0.95));
      gradient.addColorStop(0.45, hsla(hue, 100, 60, 0.82));
      gradient.addColorStop(1, hsla(hue - 25, 100, 46, 0.42));

      context.fillStyle = gradient;
      context.shadowBlur = 5 + value * 15;
      context.shadowColor = hsla(hue, 100, 58, 0.72);
      context.fillRect(x, baseY - barHeight, Math.max(1, barWidth), barHeight);

      context.fillStyle = hsla(hue, 100, 55, 0.10 + value * 0.18);
      context.fillRect(x, baseY + 2, Math.max(1, barWidth), barHeight * 0.34);
    }
    context.shadowBlur = 0;
  }

  function draw(frame, now) {
    if (!context || !frame) return;
    context.clearRect(0, 0, width, height);

    const centerX = width * 0.50;
    const centerY = height * 0.43;
    const radius = Math.min(width, height) * 0.238;
    const maxRadius = Math.sqrt(width * width + height * height) * 0.58;

    context.globalCompositeOperation = 'lighter';
    drawParticles(frame, now, centerX, centerY, maxRadius);
    drawOrbits(frame, now, centerX, centerY, radius);
    drawRadial(frame, centerX, centerY, radius);
    drawWave(frame, height * 0.705);
    drawFloor(frame);
    context.globalCompositeOperation = 'source-over';
  }

  function loop(timestamp) {
    window.requestAnimationFrame(loop);
    const now = Number(timestamp) || Date.now();
    if (now - lastFrameAt < FRAME_MS) return;
    lastFrameAt = now;

    if (now - lastCaptureAt >= FRAME_MS * 0.78) {
      lastCaptureAt = now;
      capture(now);
    }

    const frame = delayedFrame(now);
    if (!frame) return;
    applyCss(frame, now);
    draw(frame, now);
  }

  function onRemoteKey(event) {
    const keyCode = event.keyCode || event.which;
    let handled = true;

    if (keyCode === 37) setDelay(visualDelayMs - 10);
    else if (keyCode === 39) setDelay(visualDelayMs + 10);
    else if (keyCode === 38) setIntensity(intensity + 0.10);
    else if (keyCode === 40) setIntensity(intensity - 0.10);
    else if (keyCode === 13) toggleHud();
    else handled = false;

    if (handled) {
      try { event.preventDefault(); } catch (_) {}
      try { event.stopPropagation(); } catch (_) {}
    }
  }

  window.SmartIRVisualizer = {
    setDelay,
    setIntensity,
    toggleHud,
    analyser: () => latestAnalyser,
  };

  document.addEventListener('keydown', onRemoteKey, true);
  window.addEventListener('resize', resize);

  patchAudioApi();
  window.setInterval(() => {
    if (!audioPatched) patchAudioApi();
  }, 1000);

  resize();
  setDelay(35);
  setIntensity(1);
  window.requestAnimationFrame(loop);
})();
