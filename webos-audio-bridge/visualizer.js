(() => {
  'use strict';

  const root = document.documentElement;
  const hud = document.getElementById('hud');
  const syncValue = document.getElementById('syncValue');
  const canvas = document.getElementById('fxCanvas');
  const context = canvas && canvas.getContext ? canvas.getContext('2d') : null;
  const orbitShell = document.getElementById('orbitShell');

  const ACTIVE_FPS = 18;
  const ECO_FPS = 12;
  const IDLE_POLL_FPS = 3;
  const PARTICLE_COUNT = 38;
  const ECO_PARTICLE_COUNT = 18;
  const RADIAL_BARS = 48;
  const ECO_RADIAL_BARS = 32;
  const FLOOR_BARS = 32;
  const ECO_FLOOR_BARS = 20;
  const WAVE_POINTS = 64;
  const HISTORY_LIMIT = 10;
  const SIGNAL_ON_THRESHOLD = 5;
  const SIGNAL_OFF_THRESHOLD = 2;

  let frequencyData = null;
  let timeDomainData = null;
  let visualDelayMs = 40;
  let intensity = 1;
  let hudVisible = true;
  let cssWidth = 1920;
  let cssHeight = 1080;
  let width = 1152;
  let height = 648;
  let renderScale = 0.60;
  let lastFrameAt = 0;
  let bassAverage = 0;
  let huePhase = 122;
  let history = [];
  let particles = [];
  let radialGeometry = [];
  let audioActive = false;
  let signalFrames = 0;
  let silentFrames = 0;
  let staticFrameDrawn = false;
  let ecoUntil = 0;
  let lastUnderruns = 0;
  let lastDrops = 0;
  let smoothedBass = 0;
  let smoothedMid = 0;
  let smoothedTreble = 0;
  let smoothedEnergy = 0;

  function clamp(value, min, max) {
    return Math.max(min, Math.min(max, value));
  }

  function analyser() {
    const candidate = window.SmartIRAudioAnalyser;
    if (!candidate) return null;
    try {
      if (!candidate.context || candidate.context.state === 'closed') return null;
    } catch (_) {
      return null;
    }
    return candidate;
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

  function smoothValue(previous, target, attack, release) {
    const factor = target > previous ? attack : release;
    return previous + (target - previous) * factor;
  }

  function updateLabel() {
    if (!syncValue) return;
    const protectedMode = Date.now() < ecoUntil ? ' · AUDIO PROTECT' : '';
    syncValue.textContent = `SYNC +${visualDelayMs} ms · FX ${Math.round(intensity * 100)}%${protectedMode}`;
  }

  function setDelay(value) {
    visualDelayMs = Math.round(clamp(Number(value) || 0, 0, 180) / 5) * 5;
    updateLabel();
  }

  function setIntensity(value) {
    intensity = clamp(Number(value) || 1, 0.50, 1.55);
    root.style.setProperty('--intensity', intensity.toFixed(2));
    updateLabel();
  }

  function toggleHud() {
    hudVisible = !hudVisible;
    if (hud) hud.classList.toggle('hidden', !hudVisible);
  }

  function setAudioActive(active) {
    if (audioActive === active) return;
    audioActive = active;
    root.classList.toggle('audio-active', active);
    root.classList.toggle('audio-idle', !active);
    staticFrameDrawn = false;
    if (!active) {
      history = [];
      smoothedBass = 0;
      smoothedMid = 0;
      smoothedTreble = 0;
      smoothedEnergy = 0;
      applyCss({ bass: 0, mid: 0, treble: 0, energy: 0, beat: 0, hue: 132 }, 0);
    }
  }

  function reportAudioHealth(state) {
    const health = state || {};
    const underruns = Number(health.underruns) || 0;
    const drops = Number(health.drops) || 0;
    const bufferMs = Number(health.bufferMs) || 0;

    if (underruns > lastUnderruns || drops > lastDrops || (health.playing && bufferMs > 0 && bufferMs < 95)) {
      ecoUntil = Date.now() + 7000;
      root.classList.add('audio-protect');
    }
    lastUnderruns = underruns;
    lastDrops = drops;

    if (Date.now() >= ecoUntil) root.classList.remove('audio-protect');
    updateLabel();
  }

  function resize() {
    if (!canvas || !context) return;
    cssWidth = Math.max(1, window.innerWidth || 1920);
    cssHeight = Math.max(1, window.innerHeight || 1080);
    renderScale = cssWidth >= 1600 ? 0.60 : 0.72;
    width = Math.max(640, Math.round(cssWidth * renderScale));
    height = Math.max(360, Math.round(cssHeight * renderScale));
    canvas.width = width;
    canvas.height = height;
    canvas.style.width = `${cssWidth}px`;
    canvas.style.height = `${cssHeight}px`;
    context.imageSmoothingEnabled = true;
    createParticles();
    createRadialGeometry();
    staticFrameDrawn = false;
  }

  function createParticles() {
    particles = [];
    const maxRadius = Math.sqrt(width * width + height * height) * 0.56;
    for (let index = 0; index < PARTICLE_COUNT; index += 1) {
      particles.push({
        angle: Math.random() * Math.PI * 2,
        radius: Math.random() * maxRadius,
        speed: 0.08 + Math.random() * 0.27,
        size: 0.7 + Math.random() * 1.8,
        alpha: 0.18 + Math.random() * 0.48,
        hueOffset: Math.random() * 190,
        phase: Math.random() * Math.PI * 2,
      });
    }
  }

  function createRadialGeometry() {
    radialGeometry = [];
    for (let index = 0; index < RADIAL_BARS; index += 1) {
      const angle = (index / RADIAL_BARS) * Math.PI * 2 - Math.PI / 2;
      radialGeometry.push({ cos: Math.cos(angle), sin: Math.sin(angle) });
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
      bands[index] = clamp((peak * 0.64 + average * 0.36) / 255, 0, 1);
    }
    return bands;
  }

  function sampleWave(values) {
    const wave = new Float32Array(WAVE_POINTS);
    if (!values || !values.length) return wave;
    const stride = Math.max(1, Math.floor(values.length / WAVE_POINTS));
    for (let index = 0; index < WAVE_POINTS; index += 1) {
      wave[index] = ((values[Math.min(values.length - 1, index * stride)] || 128) - 128) / 128;
    }
    return wave;
  }

  function capture(now) {
    const liveAnalyser = analyser();
    let framePeak = 0;

    if (!liveAnalyser) {
      signalFrames = 0;
      silentFrames += 1;
      if (silentFrames > 4) setAudioActive(false);
      return null;
    }

    if (!frequencyData || frequencyData.length !== liveAnalyser.frequencyBinCount) {
      frequencyData = new Uint8Array(liveAnalyser.frequencyBinCount);
    }
    if (!timeDomainData || timeDomainData.length !== liveAnalyser.fftSize) {
      timeDomainData = new Uint8Array(liveAnalyser.fftSize);
    }

    liveAnalyser.getByteFrequencyData(frequencyData);
    liveAnalyser.getByteTimeDomainData(timeDomainData);

    const observedBins = Math.min(frequencyData.length - 1, 118);
    for (let index = 1; index <= observedBins; index += 1) {
      framePeak = Math.max(framePeak, frequencyData[index] || 0);
    }

    if (framePeak >= SIGNAL_ON_THRESHOLD) {
      signalFrames += 1;
      silentFrames = 0;
      if (signalFrames >= 3) setAudioActive(true);
    } else if (framePeak <= SIGNAL_OFF_THRESHOLD) {
      silentFrames += 1;
      signalFrames = Math.max(0, signalFrames - 1);
      if (silentFrames >= 18) setAudioActive(false);
    }

    if (!audioActive) return null;

    const rawBass = averageRange(frequencyData, 1, 5) / 190;
    const rawMid = averageRange(frequencyData, 6, 33) / 184;
    const rawTreble = averageRange(frequencyData, 34, 108) / 178;

    const bassTarget = Math.pow(clamp(rawBass, 0, 1), 0.72);
    const midTarget = Math.pow(clamp(rawMid, 0, 1), 0.78);
    const trebleTarget = Math.pow(clamp(rawTreble, 0, 1), 0.82);

    bassAverage = bassAverage * 0.95 + bassTarget * 0.05;
    const transient = Math.max(0, bassTarget - bassAverage);
    const beat = clamp(transient * 4.8, 0, 1);

    smoothedBass = smoothValue(smoothedBass, clamp((bassTarget * 0.74 + beat * 0.58) * intensity, 0, 1), 0.58, 0.17);
    smoothedMid = smoothValue(smoothedMid, clamp(midTarget * intensity, 0, 1), 0.42, 0.14);
    smoothedTreble = smoothValue(smoothedTreble, clamp(trebleTarget * intensity, 0, 1), 0.52, 0.20);
    const energyTarget = clamp(smoothedBass * 0.47 + smoothedMid * 0.34 + smoothedTreble * 0.19, 0, 1);
    smoothedEnergy = smoothValue(smoothedEnergy, energyTarget, 0.42, 0.14);

    huePhase = (huePhase + 0.11 + smoothedMid * 0.38 + smoothedTreble * 0.55 + beat * 0.9) % 360;
    const hue = (118 + huePhase * 0.25 + smoothedMid * 38 + smoothedTreble * 64) % 360;

    const frame = {
      time: now,
      bass: smoothedBass,
      mid: smoothedMid,
      treble: smoothedTreble,
      energy: smoothedEnergy,
      beat,
      hue,
      bands: sampleLogBands(frequencyData, FLOOR_BARS, 1, 118),
      wave: sampleWave(timeDomainData),
    };

    history.push(frame);
    while (history.length > HISTORY_LIMIT) history.shift();
    return frame;
  }

  function delayedFrame(now) {
    if (!history.length) return null;
    const target = now - visualDelayMs;
    let selected = history[0];
    for (let index = 0; index < history.length; index += 1) {
      if (history[index].time <= target) selected = history[index];
      else break;
    }
    while (history.length > 2 && history[1].time < target - 100) history.shift();
    return selected;
  }

  function applyCss(frame, now) {
    const hue2 = (frame.hue + 76 + frame.treble * 58) % 360;
    root.style.setProperty('--accent-hue', frame.hue.toFixed(1));
    root.style.setProperty('--accent-hue-2', hue2.toFixed(1));
    root.style.setProperty('--energy', frame.energy.toFixed(3));
    root.style.setProperty('--bass', frame.bass.toFixed(3));
    root.style.setProperty('--mid', frame.mid.toFixed(3));
    root.style.setProperty('--treble', frame.treble.toFixed(3));
    root.style.setProperty('--halo-scale', (1 + frame.bass * 0.050).toFixed(3));
    root.style.setProperty('--halo-opacity', (0.58 + frame.energy * 0.24).toFixed(3));
    root.style.setProperty('--leaf-scale', (0.94 + frame.bass * 0.052).toFixed(3));
    root.style.setProperty('--leaf-hue', `${(frame.hue - 132).toFixed(1)}deg`);
    root.style.setProperty('--leaf-brightness', (1 + frame.energy * 0.25).toFixed(3));
    root.style.setProperty('--aura-opacity', (0.14 + frame.energy * 0.22).toFixed(3));
    root.style.setProperty('--ambient-opacity', (0.13 + frame.energy * 0.15).toFixed(3));

    if (orbitShell) {
      const rotation = (now * 0.002 + frame.mid * 8) % 360;
      const scale = 1 + frame.beat * 0.016 + frame.energy * 0.006;
      orbitShell.style.transform =
        `translate(-50%, -50%) rotate(${rotation.toFixed(2)}deg) scale(${scale.toFixed(3)})`;
    }
  }

  function hsla(hue, saturation, lightness, alpha) {
    return `hsla(${Math.round((hue + 360) % 360)}, ${saturation}%, ${lightness}%, ${alpha})`;
  }

  function clearCanvas() {
    if (!context) return;
    context.clearRect(0, 0, width, height);
  }

  function drawStaticFrame() {
    if (!context) return;
    clearCanvas();
    const centerX = width * 0.50;
    const centerY = height * 0.43;
    const radius = Math.min(width, height) * 0.235;
    context.beginPath();
    context.strokeStyle = 'rgba(53, 245, 186, .16)';
    context.lineWidth = 1;
    context.arc(centerX, centerY, radius, 0, Math.PI * 2);
    context.stroke();
    staticFrameDrawn = true;
  }

  function drawParticles(frame, now, centerX, centerY, maxRadius, eco) {
    const count = eco ? ECO_PARTICLE_COUNT : PARTICLE_COUNT;
    const speedBoost = 0.32 + frame.energy * 1.45 + frame.beat * 0.75;
    for (let index = 0; index < count; index += 1) {
      const particle = particles[index];
      particle.radius += particle.speed * speedBoost;
      particle.angle += 0.00035 * (index % 2 ? 1 : -1) * (1 + frame.mid);
      if (particle.radius > maxRadius) {
        particle.radius = 5 + Math.random() * 20;
        particle.angle = Math.random() * Math.PI * 2;
      }

      const wobble = Math.sin(now * 0.0015 + particle.phase) * (2 + frame.treble * 6);
      const x = centerX + Math.cos(particle.angle) * (particle.radius + wobble);
      const y = centerY + Math.sin(particle.angle) * (particle.radius * 0.68 + wobble * 0.30);
      const alpha = particle.alpha * (0.28 + frame.energy * 0.62);
      const hue = frame.hue + particle.hueOffset + frame.treble * 54;
      const size = particle.size * (0.80 + frame.energy * 0.50);

      context.beginPath();
      context.fillStyle = hsla(hue, 100, 68, alpha);
      context.arc(x, y, size, 0, Math.PI * 2);
      context.fill();
    }
  }

  function drawRadial(frame, centerX, centerY, radius, eco) {
    const count = eco ? ECO_RADIAL_BARS : RADIAL_BARS;
    const stride = RADIAL_BARS / count;

    for (let item = 0; item < count; item += 1) {
      const index = Math.floor(item * stride);
      const geometry = radialGeometry[index];
      const mirrored = item < count / 2 ? item : count - 1 - item;
      const sourceIndex = Math.floor((mirrored / Math.max(1, count / 2)) * frame.bands.length);
      const value = Math.pow(clamp(frame.bands[Math.min(frame.bands.length - 1, sourceIndex)] || 0, 0, 1), 0.72);
      const inner = radius * (1.025 + frame.bass * 0.010);
      const length = 4 + value * (24 + frame.energy * 42);
      const outer = inner + length;
      const hue = frame.hue + item * (245 / count) + frame.treble * 48;

      context.beginPath();
      context.strokeStyle = hsla(hue, 100, 66, 0.28 + value * 0.68);
      context.lineWidth = 0.9 + value * 2.6;
      context.lineCap = 'round';
      context.moveTo(centerX + geometry.cos * inner, centerY + geometry.sin * inner);
      context.lineTo(centerX + geometry.cos * outer, centerY + geometry.sin * outer);
      context.stroke();
    }
  }

  function drawWave(frame, centerY) {
    const left = width * 0.13;
    const right = width * 0.87;
    const lineWidth = right - left;
    const amplitude = 8 + frame.energy * 26;
    const hue = frame.hue + 46;

    context.beginPath();
    for (let index = 0; index < frame.wave.length; index += 1) {
      const x = left + (index / (frame.wave.length - 1)) * lineWidth;
      const y = centerY + frame.wave[index] * amplitude;
      if (index === 0) context.moveTo(x, y);
      else context.lineTo(x, y);
    }
    context.lineWidth = 1.2 + frame.energy * 1.1;
    context.strokeStyle = hsla(hue, 100, 72, 0.28 + frame.energy * 0.50);
    context.stroke();
  }

  function drawFloor(frame, eco) {
    const count = eco ? ECO_FLOOR_BARS : FLOOR_BARS;
    const left = width * 0.09;
    const right = width * 0.91;
    const baseY = height * 0.785;
    const totalWidth = right - left;
    const gap = Math.max(1.5, totalWidth * 0.003);
    const barWidth = (totalWidth - gap * (count - 1)) / count;

    for (let item = 0; item < count; item += 1) {
      const sourceIndex = Math.floor((item / count) * frame.bands.length);
      const value = Math.pow(clamp(frame.bands[sourceIndex] || 0, 0, 1), 0.72);
      const barHeight = 2 + value * (24 + frame.energy * 38);
      const x = left + item * (barWidth + gap);
      const hue = frame.hue + item * (230 / count);
      context.fillStyle = hsla(hue, 100, 62, 0.32 + value * 0.62);
      context.fillRect(x, baseY - barHeight, Math.max(1, barWidth), barHeight);
    }
  }

  function draw(frame, now, eco) {
    if (!context || !frame) return;
    clearCanvas();

    const centerX = width * 0.50;
    const centerY = height * 0.43;
    const radius = Math.min(width, height) * 0.235;
    const maxRadius = Math.sqrt(width * width + height * height) * 0.55;

    context.globalCompositeOperation = 'lighter';
    drawParticles(frame, now, centerX, centerY, maxRadius, eco);
    drawRadial(frame, centerX, centerY, radius, eco);
    if (!eco) drawWave(frame, height * 0.705);
    drawFloor(frame, eco);
    context.globalCompositeOperation = 'source-over';
  }

  function loop(timestamp) {
    window.requestAnimationFrame(loop);
    const now = Number(timestamp) || Date.now();
    const eco = Date.now() < ecoUntil;
    const targetFps = audioActive ? (eco ? ECO_FPS : ACTIVE_FPS) : IDLE_POLL_FPS;
    const frameInterval = 1000 / targetFps;
    if (now - lastFrameAt < frameInterval) return;
    lastFrameAt = now;

    if (!audioActive) {
      capture(now);
      if (!staticFrameDrawn) drawStaticFrame();
      return;
    }

    capture(now);
    const frame = delayedFrame(now);
    if (!frame) return;

    const started = window.performance && performance.now ? performance.now() : Date.now();
    applyCss(frame, now);
    draw(frame, now, eco);
    const finished = window.performance && performance.now ? performance.now() : Date.now();
    if (finished - started > 18) {
      ecoUntil = Date.now() + 8000;
      root.classList.add('audio-protect');
      updateLabel();
    } else if (Date.now() >= ecoUntil) {
      root.classList.remove('audio-protect');
    }
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
    reportAudioHealth,
    analyser,
  };

  document.addEventListener('keydown', onRemoteKey, true);
  window.addEventListener('resize', resize);

  root.classList.add('audio-idle');
  root.classList.remove('audio-active');
  resize();
  setDelay(40);
  setIntensity(1);
  drawStaticFrame();
  window.requestAnimationFrame(loop);
})();
