// ============================================================================
// 【独立ビジュアルモジュール：sky_time.js】
// ・不安定なブラウザセンサー計算を削ぎ落とし、Androidネイティブ(Kotlin)の高精度/子ども向けコンパス＆純正GPSへ直結
// ・二重球体スクリーン（外側スクリーンで星空・朝日・夕焼け・天体位置を表示）
// ・設定「🔭 観察・空」タブへ統合：
//   ①ゲーム（くるくる回転）／ ②天体観察前（ハイブリッド切替）／ ③天体観察時（実機コンパス固定＋暗転停止）
// ============================================================================
(function initSkyTimeModule() {
  if (typeof THREE === 'undefined' || typeof scene === 'undefined') return;

  const STORAGE_KEY = 'island_sky_time_v15';
  const RAD = Math.PI / 180;
  const DEG = 180 / Math.PI;
  const SKY_R = 22.0;
  const INNER_DOME_R = 26.0;
  const STAR_R = 27.5;
  const OUTER_DOME_R = 30.0;
  const BASE_FOLLOW_FOV = 38.0;

  const state = {
    viewMode: 'anime',          // 'anime'(①ゲーム) | 'pre_obs'(②天体観察前) | 'obs'(③天体観察時)
    preObsHybrid: 'native',     // 'spin'(くるくる回転) | 'native'(実機コンパス連動)
    compassStyle: 'kid',        // 'precision'(高精度コンパス) | 'kid'(子ども向け磁石吸着コンパス)
    postureMode: 0,             // 0:自動判定, 1:寝かせ持ち固定, 2:立て持ち固定
    obsConstLines: true,
    turnCount: 0,
    lat: 35.68,
    lon: 139.77,
    gpsApplied: false
  };

  function loadState() {
    try {
      const store = (typeof safeStorage !== 'undefined') ? safeStorage : window.localStorage;
      const raw = store && store.getItem(STORAGE_KEY);
      if (raw) {
        const parsed = JSON.parse(raw);
        if (['anime', 'pre_obs', 'obs'].includes(parsed.viewMode)) state.viewMode = parsed.viewMode;
        if (['spin', 'native'].includes(parsed.preObsHybrid)) state.preObsHybrid = parsed.preObsHybrid;
        if (['precision', 'kid'].includes(parsed.compassStyle)) state.compassStyle = parsed.compassStyle;
        if ([0, 1, 2].includes(parsed.postureMode)) state.postureMode = parsed.postureMode;
        if (typeof parsed.obsConstLines === 'boolean') state.obsConstLines = parsed.obsConstLines;
        if (Number.isFinite(parsed.turnCount) && parsed.turnCount >= 0) state.turnCount = Math.floor(parsed.turnCount);
        if (Number.isFinite(parsed.lat)) state.lat = parsed.lat;
        if (Number.isFinite(parsed.lon)) state.lon = parsed.lon;
        if (typeof parsed.gpsApplied === 'boolean') state.gpsApplied = parsed.gpsApplied;
      }
    } catch (e) {}
  }
  function saveState() {
    try {
      const store = (typeof safeStorage !== 'undefined') ? safeStorage : window.localStorage;
      if (store) store.setItem(STORAGE_KEY, JSON.stringify(state));
    } catch (e) {}
  }
  loadState();

  // --- 実天球座標データ（主要12星座：赤経[時], 赤緯[度]） ---
  const CONSTELLATIONS = [
    { name: 'こぐま座', stars: [[2.53,89.26],[14.85,74.16],[15.35,71.83],[16.77,82.04],[17.54,86.59]], lines: [[0,4],[4,3],[3,1],[1,2]] },
    { name: '北斗七星', stars: [[11.06,61.75],[11.03,56.38],[11.90,53.69],[12.26,57.03],[12.90,55.96],[13.40,54.93],[13.79,49.31]], lines: [[0,1],[1,2],[2,3],[3,0],[3,4],[4,5],[5,6]] },
    { name: 'カシオペア座', stars: [[0.15,59.15],[0.68,56.54],[0.95,60.72],[1.43,60.24],[1.91,63.67]], lines: [[0,1],[1,2],[2,3],[3,4]] },
    { name: 'オリオン座', stars: [[5.92,7.41],[5.42,6.35],[5.53,-0.30],[5.60,-1.20],[5.68,-1.94],[5.24,-8.20],[5.80,-9.67]], lines: [[0,1],[0,4],[1,2],[2,3],[3,4],[2,5],[4,6],[5,6]] },
    { name: 'はくちょう座', stars: [[20.69,45.28],[20.37,40.26],[19.51,27.96],[19.75,45.13],[20.77,33.97]], lines: [[0,1],[1,2],[3,1],[1,4]] },
    { name: 'さそり座', stars: [[16.49,-26.43],[16.01,-19.81],[15.98,-22.62],[16.09,-26.11],[16.84,-34.29],[17.20,-43.24],[17.56,-37.10]], lines: [[1,2],[2,3],[2,0],[0,4],[4,5],[5,6]] },
    { name: 'こと座', stars: [[18.62,38.78],[18.83,33.36],[18.98,32.69],[18.91,36.90]], lines: [[0,3],[3,1],[1,2],[2,0]] },
    { name: 'わし座', stars: [[19.85,8.87],[19.77,10.61],[19.92,6.41],[19.09,13.86]], lines: [[3,1],[1,0],[0,2]] },
    { name: 'おおいぬ座', stars: [[6.75,-16.72],[6.38,-17.96],[7.14,-26.39],[7.05,-28.97],[7.40,-29.30]], lines: [[1,0],[0,2],[2,3],[2,4]] },
    { name: 'しし座', stars: [[10.14,11.97],[10.33,19.84],[10.12,16.76],[11.24,20.52],[11.82,14.57]], lines: [[0,2],[2,1],[1,3],[3,4],[4,0]] },
    { name: 'ふたご座', stars: [[7.58,31.89],[7.76,28.03],[6.63,16.40],[6.38,22.51]], lines: [[0,1],[0,3],[1,2]] },
    { name: 'ペガスス座', stars: [[23.08,15.21],[23.06,28.08],[0.14,29.09],[0.22,15.18]], lines: [[0,1],[1,2],[2,3],[3,0]] }
  ];

  // --- 「今すぐ考える」ボタン・自動思考・初期化との連動フック ---
  let lastAdvanceAt = 0;

  function advanceTurn() {
    const now = Date.now();
    if (now - lastAdvanceAt < 2200) return;
    lastAdvanceAt = now;
    state.turnCount += 1;
    saveState();
    updateCelestialExhibit(true);
  }

  function resetTurns() {
    state.turnCount = 0;
    lastAdvanceAt = 0;
    saveState();
    updateCelestialExhibit(true);
  }

  document.addEventListener('click', (e) => {
    const target = e.target && e.target.closest ? e.target.closest('button') : e.target;
    if (!target) return;
    const txt = (target.textContent || '').trim();
    if (txt.includes('今すぐ考える')) {
      advanceTurn();
    } else if (txt.includes('最初からやり直す') || txt.includes('初期化')) {
      setTimeout(resetTurns, 150);
    }
  }, true);

  if (typeof window.fetch === 'function' && !window.fetch._skyTurnHooked) {
    const _origFetch = window.fetch;
    window.fetch = function() {
      const p = _origFetch.apply(this, arguments);
      try {
        const url = String(arguments[0] || '');
        if (url.includes('groq') || url.includes('openai') || url.includes('generativelanguage') || url.includes('/api/')) {
          p.then(() => advanceTurn()).catch(() => {});
        }
      } catch (e) {}
      return p;
    };
    window.fetch._skyTurnHooked = true;
  }

  if (typeof showToast === 'function' && !showToast._skyToastHooked) {
    const _origToast = showToast;
    window.showToast = function(msg) {
      if (typeof msg === 'string' && msg.includes('初期化しました')) {
        resetTurns();
      }
      return _origToast.apply(this, arguments);
    };
    window.showToast._skyToastHooked = true;
  }

  // --- 上部バッジの時間帯テキスト同期 ---
  const PHASE_LABELS = ['朝', '昼', '夕', '夜', '深夜'];
  function syncTopPhaseBadge(phaseIdx) {
    const label = PHASE_LABELS[phaseIdx] || '朝';
    const badges = document.querySelectorAll('span, div');
    for (let i = 0; i < badges.length; i++) {
      const el = badges[i];
      if (el.children.length > 0 || el.closest('#settings-tab-panel-obs')) continue;
      const rect = el.getBoundingClientRect();
      if (rect.top > 130 || rect.height === 0) continue;
      const t = (el.textContent || '').trim();
      if (/^(☀️|🌤️|🌇|🌙|🌌)?\s*(朝|昼|夕|夜|深夜)$/.test(t)) {
        const icon = phaseIdx === 0 ? '☀️ ' : (phaseIdx === 1 ? '🌤️ ' : (phaseIdx === 2 ? '🌇 ' : (phaseIdx === 3 ? '🌙 ' : '🌌 ')));
        if (el.textContent !== icon + label) {
          el.textContent = icon + label;
        }
      }
    }
  }

  // --- ③天体観察時専用：画面の暗転停止（Screen Wake Lock API） ---
  let wakeLockSentinel = null;
  let wakeLockWanted = false;

  async function syncScreenWakeLock(shouldKeepAwake) {
    wakeLockWanted = Boolean(shouldKeepAwake);
    if (!('wakeLock' in navigator) || !navigator.wakeLock) return;

    if (wakeLockWanted) {
      if (wakeLockSentinel && !wakeLockSentinel.released) return;
      try {
        wakeLockSentinel = await navigator.wakeLock.request('screen');
        wakeLockSentinel.addEventListener('release', () => {
          wakeLockSentinel = null;
        });
      } catch (e) {}
    } else if (wakeLockSentinel) {
      try {
        await wakeLockSentinel.release();
      } catch (e) {}
      wakeLockSentinel = null;
    }
  }

  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible' && wakeLockWanted) {
      syncScreenWakeLock(true);
    }
  });

  // ============================================================================
  // --- Androidネイティブ（Kotlin）コンパス＆純正GPS直結ブリッジ ---
  // ============================================================================
  const nativeBridge = {
    connected: false,
    precisionAzimuth: 0,
    kidAzimuth: 0,
    kidSnapped: false,
    isUpright: false,
    accuracy: 2,
    userTouching: false,
    userPausedTracking: false,
    alignedNow: false,
    postureSynced: false
  };

  const tmpCamDir = new THREE.Vector3();
  const tmpOffsetVec = new THREE.Vector3();

  window.addEventListener('pointerdown', () => { nativeBridge.userTouching = true; }, { passive: true });
  window.addEventListener('pointerup', () => { nativeBridge.userTouching = false; }, { passive: true });
  window.addEventListener('pointercancel', () => { nativeBridge.userTouching = false; }, { passive: true });

  function norm360(v) { return ((v % 360) + 360) % 360; }
  function shortestAngleDiffDeg(a, b) {
    return ((a - b + 540) % 360) - 180;
  }

  function getDirectionName8(degree) {
    const d = Math.round(norm360(degree)) % 360;
    if (d >= 338 || d <= 22) return '北';
    if (d <= 67) return '北東';
    if (d <= 112) return '東';
    if (d <= 157) return '南東';
    if (d <= 202) return '南';
    if (d <= 247) return '南西';
    if (d <= 292) return '西';
    return '北西';
  }

  function getDirectionName4(degree) {
    const d = Math.round(norm360(degree)) % 360;
    if (d >= 315 || d < 45) return '北';
    if (d < 135) return '東';
    if (d < 225) return '南';
    return '西';
  }

  function isNativeCompassActive() {
    if (state.viewMode === 'obs') return true;
    if (state.viewMode === 'pre_obs' && state.preObsHybrid === 'native') return true;
    return false;
  }

  function getSelectedNativeAzimuth() {
    return (state.compassStyle === 'kid') ? nativeBridge.kidAzimuth : nativeBridge.precisionAzimuth;
  }

  function pollNativeBridge() {
    const bridge = window.AndroidCompass;
    if (!bridge || typeof bridge.getSensorJson !== 'function') {
      nativeBridge.connected = false;
      return;
    }
    try {
      const raw = bridge.getSensorJson();
      if (!raw) return;
      const d = JSON.parse(raw);
      nativeBridge.connected = true;
      if (Number.isFinite(d.p)) nativeBridge.precisionAzimuth = norm360(d.p);
      if (Number.isFinite(d.k)) nativeBridge.kidAzimuth = norm360(d.k);
      nativeBridge.kidSnapped = (d.ks === 1);
      nativeBridge.isUpright = (d.up === 1);
      if (Number.isFinite(d.acc)) nativeBridge.accuracy = d.acc;

      if (!nativeBridge.postureSynced && typeof bridge.setPostureMode === 'function') {
        bridge.setPostureMode(state.postureMode);
        nativeBridge.postureSynced = true;
      }

      if (d.gps === 1 && Number.isFinite(d.lat) && Number.isFinite(d.lon)) {
        const moved = Math.abs(state.lat - d.lat) > 0.002 || Math.abs(state.lon - d.lon) > 0.002;
        if (!state.gpsApplied || moved) {
          state.lat = Number(d.lat.toFixed(4));
          state.lon = Number(d.lon.toFixed(4));
          state.gpsApplied = true;
          saveState();
          lastCalcKey = '';
        }
      }
    } catch (e) {}
  }

  // 3Dカメラの上下仰角は保ったまま、水平方角だけを targetAzDeg（0°=北）へ合わせる
  function applyCameraAzimuthDeg(targetAzDeg) {
    if (typeof activeCamera === 'undefined' || !activeCamera) return;
    const azRad = targetAzDeg * RAD;
    const dirX = Math.sin(azRad);
    const dirZ = -Math.cos(azRad);

    if (typeof controls !== 'undefined' && controls && controls.target) {
      tmpOffsetVec.copy(activeCamera.position).sub(controls.target);
      const horizDist = Math.hypot(tmpOffsetVec.x, tmpOffsetVec.z);
      if (horizDist > 0.001) {
        activeCamera.position.x = controls.target.x - dirX * horizDist;
        activeCamera.position.z = controls.target.z - dirZ * horizDist;
        if (typeof controls.update === 'function') controls.update();
        else activeCamera.lookAt(controls.target);
      }
    } else {
      const euler = new THREE.Euler().setFromQuaternion(activeCamera.quaternion, 'YXZ');
      euler.y = -azRad;
      activeCamera.quaternion.setFromEuler(euler);
    }
  }

  function updateCompassAndCameraLoop() {
    pollNativeBridge();

    const isObsMode = (state.viewMode === 'obs');
    const isHybridPreObs = (state.viewMode === 'pre_obs');
    const compassActive = isNativeCompassActive();

    syncScreenWakeLock(isObsMode);

    if (compassActive && nativeBridge.connected && !nativeBridge.userPausedTracking && !nativeBridge.userTouching) {
      applyCameraAzimuthDeg(getSelectedNativeAzimuth());
    }

    const compMod = window.CompassRingModule;
    const userCompOn = (compMod && typeof compMod.isUserEnabled === 'function') ? compMod.isUserEnabled() : false;
    const alignActive = Boolean(isObsMode && userCompOn);

    if (compMod && typeof compMod.setObsAlignmentMode === 'function') {
      compMod.setObsAlignmentMode(alignActive);
    }

    // 北一致判定（③天体観察時）
    if (alignActive) {
      let diffDeg = 180;
      if (typeof activeCamera !== 'undefined' && activeCamera) {
        activeCamera.getWorldDirection(tmpCamDir);
        if (Math.hypot(tmpCamDir.x, tmpCamDir.z) > 0.04) {
          const azDeg = norm360(Math.atan2(tmpCamDir.x, -tmpCamDir.z) * DEG);
          diffDeg = Math.abs(shortestAngleDiffDeg(azDeg, 0));
        }
      }
      if (!nativeBridge.alignedNow && diffDeg <= 12) nativeBridge.alignedNow = true;
      else if (nativeBridge.alignedNow && diffDeg >= 16) nativeBridge.alignedNow = false;

      if (compMod && typeof compMod.updateNorthAlignment === 'function') {
        compMod.updateNorthAlignment(nativeBridge.alignedNow);
      }
    } else {
      nativeBridge.alignedNow = false;
    }

    const showSideControls = (isObsMode || isHybridPreObs);
    syncObsSideIcons(showSideControls, userCompOn, compassActive && !nativeBridge.userPausedTracking);
    syncCompassHudBadge(showSideControls && (compassActive || userCompOn));
  }

  // --- 上部方位HUDバッジ（高精度モード vs 子ども向け東西南北モード） ---
  function syncCompassHudBadge(showHud) {
    let hud = document.getElementById('obs-compass-hud-badge');
    if (!showHud) {
      if (hud) hud.style.display = 'none';
      return;
    }
    if (!hud) {
      hud = document.createElement('div');
      hud.id = 'obs-compass-hud-badge';
      hud.style.cssText = 'position:fixed;left:50%;top:116px;transform:translateX(-50%);z-index:8810;padding:5px 13px;border-radius:999px;background:rgba(15,23,42,0.84);border:1.5px solid #38bdf8;color:#f0f9ff;font-size:11.5px;font-weight:900;letter-spacing:0.03em;backdrop-filter:blur(4px);pointer-events:none;white-space:nowrap;box-shadow:0 2px 10px rgba(0,0,0,0.28);';
      document.body.appendChild(hud);
    }

    const compassActive = isNativeCompassActive();
    let dispDeg = 0;
    if (compassActive && nativeBridge.connected) {
      dispDeg = Math.round(getSelectedNativeAzimuth()) % 360;
    } else if (typeof activeCamera !== 'undefined' && activeCamera) {
      activeCamera.getWorldDirection(tmpCamDir);
      if (Math.hypot(tmpCamDir.x, tmpCamDir.z) > 0.02) {
        dispDeg = Math.round(norm360(Math.atan2(tmpCamDir.x, -tmpCamDir.z) * DEG)) % 360;
      }
    }

    const postureTag = nativeBridge.isUpright ? '立て持ち' : '寝かせ持ち';

    if (!compassActive) {
      hud.textContent = `🔄 くるくる回転モード（指ドラッグ視点）`;
      hud.style.borderColor = '#38bdf8';
    } else if (!nativeBridge.connected) {
      hud.textContent = `🧭 ブラウザ表示中（APK起動で実機センサー連動）`;
      hud.style.borderColor = '#94a3b8';
    } else if (nativeBridge.userPausedTracking) {
      hud.textContent = `✋ 一時停止中（右の📱アイコンで実機連動再開）`;
      hud.style.borderColor = '#f59e0b';
    } else if (state.compassStyle === 'kid') {
      const dir4 = getDirectionName4(dispDeg);
      const snapMark = nativeBridge.kidSnapped ? '✨ぴったり！' : 'ゆらっと追従';
      hud.textContent = `🧭 子ども向け：【${dir4}】${snapMark}（${postureTag}）`;
      hud.style.borderColor = nativeBridge.kidSnapped ? '#22c55e' : '#f59e0b';
    } else {
      const dir8 = getDirectionName8(dispDeg);
      const warn = nativeBridge.accuracy <= 1 ? ' ※8の字補正' : '';
      hud.textContent = `🔬 高精度：${dir8} ${dispDeg}°（${postureTag}）${warn}`;
      hud.style.borderColor = nativeBridge.alignedNow ? '#f472b6' : '#38bdf8';
    }
    hud.style.display = 'block';
  }

  // --- 右側縦列アイコンの下へ「🧭ホログラム」＆「📱追従一時停止/再開」を追加 ---
  function findRightSideIconContainer() {
    const sideCol = document.getElementById('side-icon-column');
    if (sideCol) {
      const refBtn = sideCol.querySelector('.side-circle-btn');
      return { container: sideCol, refBtn };
    }
    return null;
  }

  function createSideRoundIcon(id, emoji, badgeId, titleText, fallbackTopPx) {
    const found = findRightSideIconContainer();
    const btn = document.createElement('button');
    btn.id = id;
    btn.type = 'button';
    btn.title = titleText;

    if (found && found.container && found.refBtn) {
      const cs = window.getComputedStyle(found.refBtn);
      btn.className = found.refBtn.className;
      btn.style.cssText = `position:relative;width:${cs.width};height:${cs.height};border-radius:${cs.borderRadius || '50%'};background:${cs.backgroundColor || '#ffffff'};box-shadow:${cs.boxShadow || '0 3px 10px rgba(0,0,0,0.22)'};border:2px solid #cbd5e1;display:flex;align-items:center;justify-content:center;font-size:18px;cursor:pointer;padding:0;`;
      found.container.appendChild(btn);
    } else {
      btn.style.cssText = `position:fixed;right:12px;top:${fallbackTopPx}px;z-index:8820;width:44px;height:44px;border-radius:50%;background:#ffffff;border:2px solid #cbd5e1;box-shadow:0 3px 10px rgba(0,0,0,0.22);display:flex;align-items:center;justify-content:center;font-size:18px;cursor:pointer;padding:0;`;
      document.body.appendChild(btn);
    }

    btn.innerHTML = `
      <span style="line-height:1;">${emoji}</span>
      <span id="${badgeId}" style="position:absolute;bottom:-4px;right:-3px;font-size:8px;font-weight:900;padding:1px 4px;border-radius:6px;color:#ffffff;line-height:1.1;">OFF</span>
    `;
    return btn;
  }

  function syncObsSideIcons(showIcons, isCompOn, isTrackingOn) {
    let compBtn = document.getElementById('btn-side-obs-compass');
    let gyroBtn = document.getElementById('btn-side-obs-gyro');

    if (!showIcons) {
      if (compBtn) compBtn.style.display = 'none';
      if (gyroBtn) gyroBtn.style.display = 'none';
      return;
    }

    if (!compBtn) {
      compBtn = createSideRoundIcon('btn-side-obs-compass', '🧭', 'badge-side-obs-compass', 'ホログラムコンパス表示 ON/OFF', 372);
      compBtn.onclick = () => {
        const compMod = window.CompassRingModule;
        if (compMod && typeof compMod.toggleVisible === 'function') {
          const next = compMod.toggleVisible();
          if (typeof showToast === 'function') {
            showToast(next ? '🧭 ホログラムコンパス ON' : '🧭 ホログラムコンパス OFF');
          }
          updateCompassAndCameraLoop();
        }
      };
    }

    if (!gyroBtn) {
      gyroBtn = createSideRoundIcon('btn-side-obs-gyro', '📱', 'badge-side-obs-gyro', '実機コンパス追従 一時停止/再開', 424);
      gyroBtn.onclick = () => {
        if (!isNativeCompassActive()) {
          if (typeof showToast === 'function') showToast('⚙️ 設定の「観察・空」で実機コンパス連動を選べます');
          return;
        }
        nativeBridge.userPausedTracking = !nativeBridge.userPausedTracking;
        updateCompassAndCameraLoop();
        if (typeof showToast === 'function') {
          showToast(nativeBridge.userPausedTracking ? '✋ 実機コンパス追従を一時停止（指操作可）' : '📱 実機コンパス追従を再開しました');
        }
      };
    }

    compBtn.style.display = 'flex';
    compBtn.style.borderColor = isCompOn ? '#0284c7' : '#cbd5e1';
    const compBadge = document.getElementById('badge-side-obs-compass');
    if (compBadge) {
      compBadge.textContent = isCompOn ? 'ON' : 'OFF';
      compBadge.style.background = isCompOn ? '#0284c7' : '#64748b';
    }

    gyroBtn.style.display = isNativeCompassActive() ? 'flex' : 'none';
    gyroBtn.style.borderColor = isTrackingOn ? '#16a34a' : '#f59e0b';
    const gyroBadge = document.getElementById('badge-side-obs-gyro');
    if (gyroBadge) {
      gyroBadge.textContent = isTrackingOn ? '連動' : '停止';
      gyroBadge.style.background = isTrackingOn ? '#16a34a' : '#f59e0b';
    }
  }

  // --- 天文計算 ---
  function getSunInfo(date) {
    const d = (date.getTime() / 86400000 + 2440587.5) - 2451545.0;
    const L = norm360(280.460 + 0.9856474 * d);
    const g = norm360(357.528 + 0.9856003 * d);
    const lamDeg = norm360(L + 1.915 * Math.sin(g * RAD) + 0.020 * Math.sin(2 * g * RAD));
    const eps = (23.439 - 0.0000004 * d) * RAD;
    const lam = lamDeg * RAD;
    const dec = Math.asin(Math.sin(eps) * Math.sin(lam));
    const ra = norm360(Math.atan2(Math.cos(eps) * Math.sin(lam), Math.cos(lam)) * DEG);
    return { raDeg: ra, decDeg: dec * DEG, lamDeg };
  }

  function getMoonInfo(date) {
    const d = (date.getTime() / 86400000 + 2440587.5) - 2451545.0;
    const Lm = norm360(218.316 + 13.176396 * d);
    const Mm = norm360(134.963 + 13.064993 * d);
    const F  = norm360(93.272 + 13.229350 * d);
    const lamDeg = norm360(Lm + 6.289 * Math.sin(Mm * RAD));
    const betDeg = 5.128 * Math.sin(F * RAD);
    const eps = (23.439 - 0.0000004 * d) * RAD;
    const lam = lamDeg * RAD, bet = betDeg * RAD;
    const ra = norm360(Math.atan2(Math.sin(lam) * Math.cos(eps) - Math.tan(bet) * Math.sin(eps), Math.cos(lam)) * DEG);
    const dec = Math.asin(Math.max(-1, Math.min(1, Math.sin(bet) * Math.cos(eps) + Math.cos(bet) * Math.sin(eps) * Math.sin(lam)))) * DEG;
    const sun = getSunInfo(date);
    const phaseDeg = norm360(lamDeg - sun.lamDeg);
    return { raDeg: ra, decDeg: dec, phaseDeg, illum: (1 - Math.cos(phaseDeg * RAD)) * 0.5 };
  }

  function getGMSTDeg(date) {
    return norm360(280.46061837 + 360.98564736629 * ((date.getTime() / 86400000 + 2440587.5) - 2451545.0));
  }

  function raDecToAltAz(raDeg, decDeg, lstDeg, latDeg) {
    const ha = (lstDeg - raDeg) * RAD, dec = decDeg * RAD, lat = latDeg * RAD;
    const sinAlt = Math.sin(lat) * Math.sin(dec) + Math.cos(lat) * Math.cos(dec) * Math.cos(ha);
    const alt = Math.asin(Math.max(-1, Math.min(1, sinAlt)));
    const cosDen = Math.cos(lat) * Math.cos(alt);
    let cosAz = Math.abs(cosDen) > 1e-6 ? (Math.sin(dec) - Math.sin(lat) * sinAlt) / cosDen : 0;
    let az = Math.acos(Math.max(-1, Math.min(1, cosAz)));
    if (Math.sin(ha) > 0) az = Math.PI * 2 - az;
    return { alt, az };
  }

  function altAzToVec3(alt, az, r = SKY_R) {
    const c = Math.cos(alt);
    return new THREE.Vector3(r * c * Math.sin(az), r * Math.sin(alt), -r * c * Math.cos(az));
  }

  function calcSunriseSunsetLocal(baseDate, latDeg, lonDeg) {
    const noon = new Date(baseDate.getFullYear(), baseDate.getMonth(), baseDate.getDate(), 12, 0, 0);
    const { decDeg } = getSunInfo(noon);
    const lat = latDeg * RAD, dec = decDeg * RAD;
    const cosH = (Math.sin(-0.833 * RAD) - Math.sin(lat) * Math.sin(dec)) / (Math.cos(lat) * Math.cos(dec));
    const solarNoonH = 12 + (-noon.getTimezoneOffset() / 60 * 15 - lonDeg) / 15;
    if (cosH >= 1) return { sunriseH: 7.0, sunsetH: 17.0, solarNoonH };
    if (cosH <= -1) return { sunriseH: 4.0, sunsetH: 20.0, solarNoonH };
    const halfDayH = (Math.acos(cosH) * DEG) / 15;
    return { sunriseH: solarNoonH - halfDayH, sunsetH: solarNoonH + halfDayH, solarNoonH };
  }

  function getRealTimeInfo(now) {
    const times = calcSunriseSunsetLocal(now, state.lat, state.lon);
    const h = now.getHours() + now.getMinutes() / 60;
    let realPhase = 3;
    if (h < times.sunriseH) realPhase = 4;
    else if (h < 10.5) realPhase = 0;
    else if (h < times.sunsetH - 1.5) realPhase = 1;
    else if (h < times.sunsetH + 0.75) realPhase = 2;
    else realPhase = 3;

    const skyDayBase = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 12, 0, 0);
    if (h < times.sunriseH) skyDayBase.setDate(skyDayBase.getDate() - 1);
    return { realPhase, skyDayBase, times, nowH: h };
  }

  function resolveSkyMoment() {
    const now = new Date();
    const { realPhase, skyDayBase, times, nowH } = getRealTimeInfo(now);
    const turn = state.turnCount;
    const turnPhase = Math.floor(turn / 5) % 5;
    const subStep = turn % 5;

    if (state.viewMode === 'obs') {
      let moonMood = 'calm';
      if (realPhase === 3 && nowH < 22.0) moonMood = 'energetic';
      else if (realPhase === 4 && nowH >= 2.0) moonMood = 'sleepy';
      return { date: now, phase: realPhase, subStep: 2, turn, times, moonMood, dayTag: '現実リアルタイム・暗転停止' };
    }

    if (state.viewMode === 'anime') {
      const moonMood = turnPhase === 3 ? 'energetic' : 'sleepy';
      return { date: now, phase: turnPhase, subStep, turn, times, moonMood, dayTag: 'ゲーム周期' };
    }

    const refBase = new Date(skyDayBase.getTime());
    const isNextDay = Math.min(3, turnPhase) < Math.min(3, realPhase);
    if (isNextDay) refBase.setDate(refBase.getDate() + 1);
    const refTimes = calcSunriseSunsetLocal(refBase, state.lat, state.lon);

    const stepRatio = subStep / 4;
    let refDate;
    let moonMood = 'calm';
    if (turnPhase === 0) {
      const startH = refTimes.sunriseH + 0.25;
      const endH = Math.max(startH + 1.0, 10.2);
      const th = startH + (endH - startH) * stepRatio;
      refDate = new Date(refBase.getFullYear(), refBase.getMonth(), refBase.getDate(), Math.floor(th), Math.floor((th % 1) * 60), 0);
    } else if (turnPhase === 1) {
      const startH = 10.5;
      const endH = Math.max(startH + 1.5, refTimes.sunsetH - 1.6);
      const th = startH + (endH - startH) * stepRatio;
      refDate = new Date(refBase.getFullYear(), refBase.getMonth(), refBase.getDate(), Math.floor(th), Math.floor((th % 1) * 60), 0);
    } else if (turnPhase === 2) {
      const startH = refTimes.sunsetH - 1.4;
      const endH = refTimes.sunsetH - 0.15;
      const th = startH + (endH - startH) * stepRatio;
      refDate = new Date(refBase.getFullYear(), refBase.getMonth(), refBase.getDate(), Math.floor(th), Math.floor((th % 1) * 60), 0);
    } else if (turnPhase === 3) {
      const th = 19.0 + stepRatio * 4.5;
      refDate = new Date(refBase.getFullYear(), refBase.getMonth(), refBase.getDate(), Math.floor(th), Math.floor((th % 1) * 60), 0);
      moonMood = 'energetic';
    } else {
      const nextCal = new Date(refBase.getTime());
      nextCal.setDate(nextCal.getDate() + 1);
      const th = 0.5 + stepRatio * Math.max(2.0, refTimes.sunriseH - 1.0);
      refDate = new Date(nextCal.getFullYear(), nextCal.getMonth(), nextCal.getDate(), Math.floor(th), Math.floor((th % 1) * 60), 0);
      moonMood = 'sleepy';
    }
    return { date: refDate, phase: turnPhase, subStep, turn, times: refTimes, moonMood, dayTag: isNextDay ? '翌日空シミュ' : '当日空シミュ' };
  }

  // --- テクスチャ生成ユーティリティ ---
  function makeCanvasTex(w, h, drawFn) {
    const cv = document.createElement('canvas');
    cv.width = w; cv.height = h;
    drawFn(cv.getContext('2d'), w, h);
    const tex = new THREE.CanvasTexture(cv);
    tex.needsUpdate = true;
    return tex;
  }

  const texOuterSpace = makeCanvasTex(1024, 512, (ctx, w, h) => {
    const g = ctx.createLinearGradient(0, 0, 0, h);
    g.addColorStop(0.00, '#020617');
    g.addColorStop(0.45, '#07122a');
    g.addColorStop(0.55, '#0c1b3a');
    g.addColorStop(1.00, '#020617');
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, w, h);
    let seed = 1337;
    const rand = () => { seed = (seed * 16807) % 2147483647; return (seed - 1) / 2147483646; };
    for (let i = 0; i < 240; i++) {
      const x = rand() * w;
      const y = rand() * (h * 0.56);
      const r = 0.7 + rand() * 1.3;
      const a = 0.25 + rand() * 0.65;
      ctx.fillStyle = `rgba(224, 242, 254, ${a.toFixed(2)})`;
      ctx.beginPath(); ctx.arc(x, y, r, 0, Math.PI * 2); ctx.fill();
    }
  });

  const SKY_PALETTES = {
    anime: [
      { stops: [[0,'#7dd3fc'],[0.34,'#bae6fd'],[0.49,'#fde68a'],[0.54,'#fbcfe8'],[1,'#bae6fd']], op: 1.00 },
      { stops: [[0,'#38bdf8'],[0.32,'#7dd3fc'],[0.50,'#e0f2fe'],[0.56,'#bae6fd'],[1,'#7dd3fc']], op: 1.00 },
      { stops: [[0,'#818cf8'],[0.28,'#f472b6'],[0.48,'#fdba74'],[0.55,'#fde68a'],[1,'#c4b5fd']], op: 1.00 },
      { stops: [[0,'#1e1b4b'],[0.35,'#312e81'],[0.52,'#4c1d95'],[1,'#1e1b4b']], op: 0.48 },
      { stops: [[0,'#090d24'],[0.38,'#11183c'],[0.54,'#1e2959'],[1,'#090d24']], op: 0.24 }
    ],
    pre_obs: [
      { stops: [[0,'#3b82f6'],[0.40,'#93c5fd'],[0.51,'#fed7aa'],[1,'#64748b']], op: 1.00 },
      { stops: [[0,'#2563eb'],[0.42,'#60a5fa'],[0.54,'#dbeafe'],[1,'#64748b']], op: 1.00 },
      { stops: [[0,'#1e3a8a'],[0.36,'#7c3aed'],[0.50,'#fb923c'],[1,'#334155']], op: 1.00 },
      { stops: [[0,'#091326'],[0.45,'#172554'],[1,'#091326']], op: 0.40 },
      { stops: [[0,'#020617'],[0.45,'#0f172a'],[1,'#020617']], op: 0.18 }
    ]
  };
  const skyDomeTexCache = {};
  function getStaticSkyDomeStyle(styleKey, phase) {
    const p = Math.max(0, Math.min(4, phase));
    const key = `${styleKey}_${p}`;
    const def = SKY_PALETTES[styleKey][p];
    if (!skyDomeTexCache[key]) {
      skyDomeTexCache[key] = makeCanvasTex(32, 256, (ctx, w, h) => {
        const g = ctx.createLinearGradient(0, 0, 0, h);
        def.stops.forEach(([pos, col]) => g.addColorStop(pos, col));
        ctx.fillStyle = g;
        ctx.fillRect(0, 0, w, h);
      });
    }
    return { tex: skyDomeTexCache[key], opacity: def.op };
  }

  const obsSkyCanvas = document.createElement('canvas');
  obsSkyCanvas.width = 32; obsSkyCanvas.height = 256;
  const texObsDynamicSky = new THREE.CanvasTexture(obsSkyCanvas);
  let lastObsAltBucket = -999;

  function lerpHex(hexA, hexB, t) {
    const k = Math.max(0, Math.min(1, t));
    const a = parseInt(hexA.slice(1), 16), b = parseInt(hexB.slice(1), 16);
    const r = Math.round(((a >> 16) & 255) * (1 - k) + ((b >> 16) & 255) * k);
    const g = Math.round(((a >> 8) & 255) * (1 - k) + ((b >> 8) & 255) * k);
    const bl = Math.round((a & 255) * (1 - k) + (b & 255) * k);
    return `rgb(${r},${g},${bl})`;
  }

  function getRealtimeStylishSky(sunAltDeg) {
    const bucket = Math.round(sunAltDeg * 2);
    let zenith, mid, horizon, ground, opacity;

    if (sunAltDeg >= 18) {
      const t = Math.min(1, (sunAltDeg - 18) / 25);
      zenith  = lerpHex('#1d4ed8', '#0f3d91', t);
      mid     = lerpHex('#3b82f6', '#2563eb', t);
      horizon = lerpHex('#bae6fd', '#93c5fd', t);
      ground  = '#334155';
      opacity = 1.0;
    } else if (sunAltDeg >= 4) {
      const t = (sunAltDeg - 4) / 14;
      zenith  = lerpHex('#1e293b', '#1d4ed8', t);
      mid     = lerpHex('#475569', '#3b82f6', t);
      horizon = lerpHex('#fdba74', '#bae6fd', t);
      ground  = '#1e293b';
      opacity = 1.0;
    } else if (sunAltDeg >= -5) {
      const t = (sunAltDeg + 5) / 9;
      zenith  = lerpHex('#090d24', '#1e293b', t);
      mid     = lerpHex('#312e81', '#6d28d9', t);
      horizon = lerpHex('#fb7185', '#fdba74', t);
      ground  = '#0f172a';
      opacity = 0.72 + t * 0.28;
    } else if (sunAltDeg >= -14) {
      const t = (sunAltDeg + 14) / 9;
      zenith  = lerpHex('#020617', '#090d24', t);
      mid     = lerpHex('#091326', '#312e81', t);
      horizon = lerpHex('#1e293b', '#fb7185', t);
      ground  = '#020617';
      opacity = 0.22 + t * 0.50;
    } else {
      zenith  = '#020617';
      mid     = '#071024';
      horizon = '#0f172a';
      ground  = '#020617';
      opacity = 0.14;
    }

    if (bucket !== lastObsAltBucket) {
      lastObsAltBucket = bucket;
      const ctx = obsSkyCanvas.getContext('2d');
      const g = ctx.createLinearGradient(0, 0, 0, 256);
      g.addColorStop(0.00, zenith);
      g.addColorStop(0.35, mid);
      g.addColorStop(0.50, horizon);
      g.addColorStop(0.56, ground);
      g.addColorStop(1.00, '#020617');
      ctx.fillStyle = g;
      ctx.fillRect(0, 0, 32, 256);
      texObsDynamicSky.needsUpdate = true;
    }
    return { tex: texObsDynamicSky, opacity };
  }

  // --- かわいい顔＆天体スプライト ---
  function drawCuteFace(ctx, cx, cy, mood, ink, cheekRgba, isProfile = false) {
    ctx.fillStyle = cheekRgba;
    if (isProfile) {
      ctx.beginPath(); ctx.ellipse(cx - 2, cy + 12, 9, 5, 0, 0, Math.PI * 2); ctx.fill();
    } else {
      ctx.beginPath(); ctx.ellipse(cx - 33, cy + 11, 10, 6, 0, 0, Math.PI * 2); ctx.ellipse(cx + 33, cy + 11, 10, 6, 0, 0, Math.PI * 2); ctx.fill();
    }
    ctx.strokeStyle = ink; ctx.fillStyle = ink; ctx.lineCap = 'round';
    const eyes = isProfile ? [[cx - 10, cy - 4]] : [[cx - 21, cy - 6], [cx + 21, cy - 6]];
    if (mood === 'energetic') {
      eyes.forEach(([ex, ey]) => {
        ctx.beginPath(); ctx.arc(ex, ey, 7.6, 0, Math.PI * 2); ctx.fill();
        ctx.fillStyle = '#ffffff'; ctx.beginPath(); ctx.arc(ex - 2.3, ey - 2.6, 2.8, 0, Math.PI * 2); ctx.fill();
        ctx.fillStyle = ink;
      });
      ctx.beginPath(); ctx.arc(cx, cy + 6, isProfile ? 10 : 14, 0, Math.PI); ctx.fill();
      ctx.fillStyle = '#fb7185'; ctx.beginPath(); ctx.arc(cx, cy + 12, isProfile ? 5.5 : 7.5, 0, Math.PI); ctx.fill();
    } else if (mood === 'calm') {
      eyes.forEach(([ex, ey]) => { ctx.beginPath(); ctx.arc(ex, ey, 6.4, 0, Math.PI * 2); ctx.fill(); });
      ctx.lineWidth = 4.6;
      ctx.beginPath(); ctx.arc(cx, cy + 5, isProfile ? 9 : 13, 0.16 * Math.PI, 0.84 * Math.PI); ctx.stroke();
    } else {
      ctx.lineWidth = 4.6;
      eyes.forEach(([ex, ey]) => { ctx.beginPath(); ctx.arc(ex, ey - 2, 7.5, 0.12 * Math.PI, 0.88 * Math.PI); ctx.stroke(); });
      ctx.lineWidth = 4.0;
      ctx.beginPath(); ctx.ellipse(cx, cy + 12, isProfile ? 5 : 6.5, 5, 0, 0, Math.PI * 2); ctx.stroke();
    }
  }

  const texSunRays = makeCanvasTex(256, 256, (ctx, s) => {
    const c = s / 2;
    ctx.fillStyle = '#fdba74';
    for (let i = 0; i < 12; i++) {
      ctx.save(); ctx.translate(c, c); ctx.rotate((i / 12) * Math.PI * 2);
      ctx.beginPath(); ctx.moveTo(-12, -74); ctx.lineTo(12, -74); ctx.lineTo(0, -118); ctx.closePath(); ctx.fill();
      ctx.restore();
    }
  });

  const texSunAnime = {};
  ['energetic', 'calm', 'sleepy'].forEach((mood) => {
    texSunAnime[mood] = makeCanvasTex(256, 256, (ctx, s) => {
      const c = s / 2;
      const g = ctx.createRadialGradient(c, c, 46, c, c, 120);
      g.addColorStop(0, mood === 'sleepy' ? 'rgba(251,146,60,0.55)' : 'rgba(255,198,128,0.58)');
      g.addColorStop(1, 'rgba(255,198,128,0)');
      ctx.fillStyle = g; ctx.beginPath(); ctx.arc(c, c, 120, 0, Math.PI * 2); ctx.fill();
      ctx.fillStyle = mood === 'sleepy' ? '#fb923c' : '#f97316';
      ctx.beginPath(); ctx.arc(c, c, 70, 0, Math.PI * 2); ctx.fill();
      ctx.fillStyle = mood === 'sleepy' ? '#fdba74' : '#fed7aa';
      ctx.beginPath(); ctx.arc(c - 7, c - 7, 58, 0, Math.PI * 2); ctx.fill();
      drawCuteFace(ctx, c, c, mood, '#7c2d12', 'rgba(251,113,133,0.52)', false);
    });
  });

  const texMoonAnime = {};
  ['full', 'crescent'].forEach((shape) => {
    texMoonAnime[shape] = {};
    ['energetic', 'calm', 'sleepy'].forEach((mood) => {
      texMoonAnime[shape][mood] = makeCanvasTex(256, 256, (ctx, s) => {
        const c = s / 2;
        const g = ctx.createRadialGradient(c, c, 44, c, c, 118);
        g.addColorStop(0, 'rgba(254,240,138,0.48)'); g.addColorStop(1, 'rgba(254,240,138,0)');
        ctx.fillStyle = g; ctx.beginPath(); ctx.arc(c, c, 118, 0, Math.PI * 2); ctx.fill();
        if (shape === 'full') {
          ctx.fillStyle = '#fde047'; ctx.beginPath(); ctx.arc(c, c, 68, 0, Math.PI * 2); ctx.fill();
          ctx.fillStyle = '#fef08a'; ctx.beginPath(); ctx.arc(c - 6, c - 6, 56, 0, Math.PI * 2); ctx.fill();
          drawCuteFace(ctx, c, c, mood, '#713f12', 'rgba(251,146,60,0.34)', false);
        } else {
          const off = document.createElement('canvas'); off.width = s; off.height = s;
          const octx = off.getContext('2d');
          octx.fillStyle = '#fde047'; octx.beginPath(); octx.arc(c, c, 70, 0, Math.PI * 2); octx.fill();
          octx.globalCompositeOperation = 'destination-out';
          octx.beginPath(); octx.arc(c + 34, c - 14, 58, 0, Math.PI * 2); octx.fill();
          ctx.drawImage(off, 0, 0);
          drawCuteFace(ctx, c - 22, c + 2, mood, '#713f12', 'rgba(251,146,60,0.34)', true);
        }
      });
    });
  });

  const texSunReal = makeCanvasTex(256, 256, (ctx, s) => {
    const c = s / 2, g = ctx.createRadialGradient(c, c, 14, c, c, 124);
    g.addColorStop(0, 'rgba(255,255,250,1)'); g.addColorStop(0.28, 'rgba(255,244,214,0.96)');
    g.addColorStop(0.46, 'rgba(253,224,150,0.48)'); g.addColorStop(0.75, 'rgba(186,230,253,0.14)'); g.addColorStop(1, 'rgba(56,189,248,0)');
    ctx.fillStyle = g; ctx.fillRect(0, 0, s, s);
  });

  const moonRealCanvas = document.createElement('canvas');
  moonRealCanvas.width = 256; moonRealCanvas.height = 256;
  const texMoonReal = new THREE.CanvasTexture(moonRealCanvas);
  let lastMoonBucket = -1;
  function updateRealMoonTexture(phaseDeg) {
    const b = Math.round(phaseDeg / 6);
    if (b === lastMoonBucket) return;
    lastMoonBucket = b;
    const ctx = moonRealCanvas.getContext('2d'), s = 256, c = 128, r = 82;
    ctx.clearRect(0, 0, s, s);
    const halo = ctx.createRadialGradient(c, c, r * 0.85, c, c, 124);
    halo.addColorStop(0, 'rgba(224,242,254,0.28)'); halo.addColorStop(1, 'rgba(224,242,254,0)');
    ctx.fillStyle = halo; ctx.beginPath(); ctx.arc(c, c, 124, 0, Math.PI * 2); ctx.fill();
    ctx.save(); ctx.beginPath(); ctx.arc(c, c, r, 0, Math.PI * 2); ctx.clip();
    const bg = ctx.createRadialGradient(c - 18, c - 18, 12, c, c, r);
    bg.addColorStop(0, '#f8fafc'); bg.addColorStop(0.7, '#e2e8f0'); bg.addColorStop(1, '#cbd5e1');
    ctx.fillStyle = bg; ctx.fillRect(0, 0, s, s);
    ctx.fillStyle = 'rgba(148,163,184,0.28)';
    [[c-22,c-18,22],[c+18,c-8,16],[c-6,c+24,20],[c+26,c+22,12]].forEach(([x,y,cr]) => { ctx.beginPath(); ctx.arc(x,y,cr,0,Math.PI*2); ctx.fill(); });
    const k = Math.cos(phaseDeg * RAD);
    ctx.fillStyle = 'rgba(15,23,42,0.86)'; ctx.beginPath();
    if (phaseDeg <= 180) {
      ctx.arc(c, c, r + 1, Math.PI / 2, Math.PI * 1.5, false);
      ctx.ellipse(c, c, Math.abs(k) * r, r + 1, 0, -Math.PI / 2, Math.PI / 2, k < 0);
    } else {
      ctx.arc(c, c, r + 1, -Math.PI / 2, Math.PI / 2, false);
      ctx.ellipse(c, c, Math.abs(k) * r, r + 1, 0, Math.PI / 2, Math.PI * 1.5, k < 0);
    }
    ctx.closePath(); ctx.fill(); ctx.restore();
    texMoonReal.needsUpdate = true;
  }

  function createMeasureTex(fill, stroke) {
    return makeCanvasTex(256, 256, (ctx, s) => {
      const c = s / 2;
      ctx.fillStyle = fill; ctx.beginPath(); ctx.arc(c, c, 82, 0, Math.PI * 2); ctx.fill();
      ctx.strokeStyle = stroke; ctx.lineWidth = 8; ctx.beginPath(); ctx.arc(c, c, 82, 0, Math.PI * 2); ctx.stroke();
      ctx.lineWidth = 3; ctx.beginPath(); ctx.arc(c, c, 102, 0, Math.PI * 2); ctx.stroke();
    });
  }
  const texSunObs  = createMeasureTex('rgba(250, 204, 21, 0.36)', 'rgba(253, 224, 71, 0.88)');
  const texMoonObs = createMeasureTex('rgba(255, 255, 255, 0.34)', 'rgba(241, 245, 249, 0.90)');

  const texStarCircleDigital = makeCanvasTex(64, 64, (ctx, s) => {
    const g = ctx.createRadialGradient(32, 32, 2, 32, 32, 30);
    g.addColorStop(0.0, 'rgba(255,255,255,1)');
    g.addColorStop(0.35, 'rgba(224,242,254,0.92)');
    g.addColorStop(0.65, 'rgba(56,189,248,0.40)');
    g.addColorStop(1.0, 'rgba(56,189,248,0)');
    ctx.fillStyle = g; ctx.beginPath(); ctx.arc(32, 32, 30, 0, Math.PI * 2); ctx.fill();
  });
  const texStarCirclePastel = makeCanvasTex(64, 64, (ctx, s) => {
    const g = ctx.createRadialGradient(32, 32, 2, 32, 32, 30);
    g.addColorStop(0.0, 'rgba(255,251,235,1)');
    g.addColorStop(0.38, 'rgba(254,240,138,0.90)');
    g.addColorStop(0.68, 'rgba(249,168,212,0.40)');
    g.addColorStop(1.0, 'rgba(249,168,212,0)');
    ctx.fillStyle = g; ctx.beginPath(); ctx.arc(32, 32, 30, 0, Math.PI * 2); ctx.fill();
  });

  // --- 3D二重球体スクリーン＋天体グループ ---
  const skyRoot = new THREE.Group();
  skyRoot.name = 'holo_sky_exhibit_root';
  scene.add(skyRoot);

  const outerSpaceMat = new THREE.MeshBasicMaterial({ map: texOuterSpace, side: THREE.BackSide, depthWrite: false });
  const outerSpaceDome = new THREE.Mesh(new THREE.SphereGeometry(OUTER_DOME_R, 36, 24), outerSpaceMat);
  outerSpaceDome.renderOrder = -20;
  skyRoot.add(outerSpaceDome);

  const starGroup = new THREE.Group();
  const starPointsMat = new THREE.PointsMaterial({ map: texStarCircleDigital, color: 0xe0f2fe, size: 1.0, transparent: true, opacity: 0.96, blending: THREE.AdditiveBlending, depthWrite: false });
  const starLineSolidMat = new THREE.LineBasicMaterial({ color: 0x38bdf8, transparent: true, opacity: 0.44, blending: THREE.AdditiveBlending, depthWrite: false });
  const starLineDashMat  = new THREE.LineDashedMaterial({ color: 0xf9a8d4, dashSize: 0.75, gapSize: 0.45, transparent: true, opacity: 0.64, depthWrite: false });
  const starPoints = new THREE.Points(new THREE.BufferGeometry(), starPointsMat);
  const starLines  = new THREE.LineSegments(new THREE.BufferGeometry(), starLineSolidMat);
  starPoints.renderOrder = -15;
  starLines.renderOrder = -16;
  starGroup.add(starLines, starPoints);
  skyRoot.add(starGroup);

  const innerSkyMat = new THREE.MeshBasicMaterial({ map: getStaticSkyDomeStyle('anime', 0).tex, side: THREE.BackSide, transparent: true, opacity: 1.0, depthWrite: false });
  const innerSkyDome = new THREE.Mesh(new THREE.SphereGeometry(INNER_DOME_R, 36, 24), innerSkyMat);
  innerSkyDome.renderOrder = -10;
  skyRoot.add(innerSkyDome);

  const sunOrbitMat  = new THREE.LineBasicMaterial({ color: 0xbae6fd, transparent: true, opacity: 0.46, depthWrite: false });
  const moonOrbitMat = new THREE.LineBasicMaterial({ color: 0x7dd3fc, transparent: true, opacity: 0.34, depthWrite: false });
  const sunOrbitLine  = new THREE.Line(new THREE.BufferGeometry(), sunOrbitMat);
  const moonOrbitLine = new THREE.Line(new THREE.BufferGeometry(), moonOrbitMat);
  sunOrbitLine.renderOrder = -6;
  moonOrbitLine.renderOrder = -6;
  skyRoot.add(sunOrbitLine, moonOrbitLine);

  const sunRayMat     = new THREE.SpriteMaterial({ map: texSunRays, transparent: true, opacity: 0.9, depthWrite: false });
  const sunSpriteMat  = new THREE.SpriteMaterial({ map: texSunAnime.energetic, transparent: true, depthWrite: false });
  const moonSpriteMat = new THREE.SpriteMaterial({ map: texMoonAnime.full.energetic, transparent: true, depthWrite: false });
  const sunRaySprite  = new THREE.Sprite(sunRayMat);
  const sunSprite     = new THREE.Sprite(sunSpriteMat);
  const moonSprite    = new THREE.Sprite(moonSpriteMat);
  sunRaySprite.renderOrder = -4;
  sunSprite.renderOrder = -3;
  moonSprite.renderOrder = -3;
  skyRoot.add(sunRaySprite, sunSprite, moonSprite);

  skyRoot.traverse((obj) => { obj.frustumCulled = false; });

  // --- カメラ画角(FOV)連動のサイズ自動補正 ---
  let baseSunScale = 3.1;
  let baseMoonScale = 2.9;
  let baseStarSize = 1.25;
  let currentRayMode = 'off';

  function getFovRatio() {
    if (typeof activeCamera === 'undefined' || !activeCamera || !Number.isFinite(activeCamera.fov)) return 1.0;
    const raw = Math.tan((activeCamera.fov * 0.5) * RAD) / Math.tan((BASE_FOLLOW_FOV * 0.5) * RAD);
    return Math.max(0.75, Math.min(2.6, raw));
  }

  function applyDynamicCameraScales(nowSec) {
    const fovRatio = getFovRatio();
    const isAnime = (state.viewMode === 'anime');
    const bodyMul = isAnime ? fovRatio : (1 + (fovRatio - 1) * 0.42);
    const starMul = isAnime ? (1 + (fovRatio - 1) * 0.72) : (1 + (fovRatio - 1) * 0.35);

    const sScale = baseSunScale * bodyMul;
    const mScale = baseMoonScale * bodyMul;
    sunSprite.scale.set(sScale, sScale, 1);
    moonSprite.scale.set(mScale, mScale, 1);
    starPointsMat.size = baseStarSize * starMul;

    if (sunRaySprite.visible) {
      if (currentRayMode === 'morning') {
        sunRayMat.rotation = nowSec * 0.35;
        const pulse = 1 + 0.07 * Math.sin(nowSec * 4.2);
        const rScale = sScale * 1.24 * pulse;
        sunRaySprite.scale.set(rScale, rScale, 1);
        sunRayMat.opacity = 0.78 + 0.22 * Math.sin(nowSec * 5.0);
      } else if (currentRayMode === 'noon') {
        sunRayMat.rotation = nowSec * 0.12;
        const rScale = sScale * 1.08;
        sunRaySprite.scale.set(rScale, rScale, 1);
        sunRayMat.opacity = 0.44 + 0.08 * Math.sin(nowSec * 2.0);
      }
    }
  }

  // --- 元アプリ側の旧3D星座看板等の非表示 ---
  const CONST_KW = ['オリオン座', '北斗七星', 'カシオペア', '夏の大三角', 'さそり座', 'こぐま座', 'はくちょう座', 'こと座', 'わし座', 'おおいぬ座', 'しし座', 'ふたご座', 'ペガスス座'];
  const tmpWorldPos = new THREE.Vector3();

  function isOwnObject(obj) {
    let cur = obj;
    while (cur) {
      if (cur === skyRoot || (cur.name && cur.name.startsWith('holo_'))) return true;
      cur = cur.parent;
    }
    return false;
  }

  function hideLegacySkyObjects() {
    scene.traverse((obj) => {
      if (!obj || isOwnObject(obj) || !obj.visible) return;
      const nm = (obj.name || '').toLowerCase();
      let hide = nm.includes('star') || nm.includes('constell') || nm.includes('orion') || nm.includes('zodiac') || nm.includes('label');

      if (!hide && (obj.isSprite || obj.isLine || obj.isLineSegments || obj.isPoints)) {
        obj.getWorldPosition(tmpWorldPos);
        const dist = tmpWorldPos.length();
        if (obj.isSprite && dist > 6.0) {
          hide = true;
        } else if ((obj.isLine || obj.isLineSegments) && obj.geometry) {
          if (!obj.geometry.boundingSphere) obj.geometry.computeBoundingSphere();
          const rad = obj.geometry.boundingSphere ? obj.geometry.boundingSphere.radius : 0;
          if (dist > 6.5 || rad > 4.5) hide = true;
        } else if (obj.isPoints && obj.geometry && obj.geometry.attributes && obj.geometry.attributes.position) {
          if (obj.geometry.attributes.position.count >= 8) {
            if (!obj.geometry.boundingSphere) obj.geometry.computeBoundingSphere();
            const rad = obj.geometry.boundingSphere ? obj.geometry.boundingSphere.radius : 0;
            if (rad > 4.5) hide = true;
          }
        }
      }

      if (!hide && obj.isMesh) {
        obj.getWorldPosition(tmpWorldPos);
        if (tmpWorldPos.length() > 9.0 && tmpWorldPos.y > 1.2 && obj.material && obj.material.color) {
          const col = obj.material.color;
          if (col.r > 0.82 && col.g > 0.70 && col.b < 0.52) hide = true;
        }
      }

      if (hide) obj.visible = false;
    });

    document.querySelectorAll('div, span').forEach((el) => {
      if (!el || el.id === 'env-skytime-status' || el.style.display === 'none') return;
      const txt = (el.textContent || '').trim();
      if (txt.length > 0 && txt.length <= 20 && CONST_KW.some((kw) => txt.includes(kw))) {
        el.style.display = 'none';
      }
    });
  }

  // --- 天体・二重球体スクリーンのメイン更新 ---
  let lastCalcKey = '';
  let lastStatusText = '';

  function formatHM(hVal) {
    const hh = Math.floor(((hVal % 24) + 24) % 24);
    const mm = Math.floor((hVal - Math.floor(hVal)) * 60);
    return `${String(hh).padStart(2, '0')}:${String(mm).padStart(2, '0')}`;
  }

  function buildBodyOrbitPoints(y, mo, da, getBodyFn, radius) {
    const pts = [];
    for (let m = 0; m <= 1440; m += 20) {
      const sample = new Date(y, mo, da, Math.floor(m / 60), m % 60, 0);
      const body = getBodyFn(sample);
      const lst = norm360(getGMSTDeg(sample) + state.lon);
      const { alt, az } = raDecToAltAz(body.raDeg, body.decDeg, lst, state.lat);
      if (alt >= -0.01) pts.push(altAzToVec3(Math.max(0.01, alt), az, radius));
    }
    return pts;
  }

  function updateCelestialExhibit(force = false) {
    hideLegacySkyObjects();
    updateCompassAndCameraLoop();

    if (typeof activeCamera !== 'undefined' && activeCamera) {
      skyRoot.position.copy(activeCamera.position);
      if (activeCamera.far < 120) {
        activeCamera.far = 150;
        activeCamera.updateProjectionMatrix();
      }
    }

    const moment = resolveSkyMoment();
    syncTopPhaseBadge(moment.phase);

    const key = `${state.viewMode}-${state.obsConstLines}-${moment.turn}-${moment.phase}-${moment.subStep}-${moment.dayTag}-${moment.date.getFullYear()}-${moment.date.getMonth()}-${moment.date.getDate()}-${moment.date.getHours()}-${Math.floor(moment.date.getMinutes() / 2)}-${state.lat.toFixed(2)}-${state.lon.toFixed(2)}`;
    if (!force && key === lastCalcKey) {
      applyDynamicCameraScales(performance.now() * 0.001);
      return;
    }
    lastCalcKey = key;

    const isAnime  = (state.viewMode === 'anime');
    const isPreObs = (state.viewMode === 'pre_obs');
    const isObs    = (state.viewMode === 'obs');
    const curLst = norm360(getGMSTDeg(moment.date) + state.lon);
    const curSun = getSunInfo(moment.date);
    const curMoon = getMoonInfo(moment.date);
    const sunPos = raDecToAltAz(curSun.raDeg, curSun.decDeg, curLst, state.lat);
    const moonPos = raDecToAltAz(curMoon.raDeg, curMoon.decDeg, curLst, state.lat);

    const isNight = isObs ? (sunPos.alt * DEG < -5.0) : (moment.phase >= 3);

    // 0. 外側二重球体スクリーン表示
    if (isAnime) {
      const domeStyle = getStaticSkyDomeStyle('anime', moment.phase);
      innerSkyDome.visible = true;
      innerSkyMat.map = domeStyle.tex;
      innerSkyMat.opacity = domeStyle.opacity;
      innerSkyMat.needsUpdate = true;
      outerSpaceDome.visible = isNight;
    } else if (isPreObs) {
      const domeStyle = getStaticSkyDomeStyle('pre_obs', moment.phase);
      innerSkyDome.visible = true;
      innerSkyMat.map = domeStyle.tex;
      innerSkyMat.opacity = domeStyle.opacity;
      innerSkyMat.needsUpdate = true;
      outerSpaceDome.visible = isNight;
    } else {
      const obsDome = getRealtimeStylishSky(sunPos.alt * DEG);
      innerSkyDome.visible = true;
      innerSkyMat.map = obsDome.tex;
      innerSkyMat.opacity = obsDome.opacity;
      innerSkyMat.needsUpdate = true;
      outerSpaceDome.visible = isNight;
    }

    // 1. 軌道線
    const y = moment.date.getFullYear(), mo = moment.date.getMonth(), da = moment.date.getDate();
    if (isAnime) {
      sunOrbitLine.visible = false;
      moonOrbitLine.visible = false;
    } else {
      const sPts = buildBodyOrbitPoints(y, mo, da, getSunInfo, SKY_R - 0.8);
      sunOrbitLine.geometry.dispose();
      sunOrbitLine.geometry = new THREE.BufferGeometry().setFromPoints(sPts);
      sunOrbitLine.visible = (sPts.length >= 2) && !isNight;
      sunOrbitMat.opacity = (moment.phase === 1) ? 0.34 : 0.48;

      const mPts = buildBodyOrbitPoints(y, mo, da, getMoonInfo, SKY_R - 1.2);
      moonOrbitLine.geometry.dispose();
      moonOrbitLine.geometry = new THREE.BufferGeometry().setFromPoints(mPts);
      moonOrbitLine.visible = (mPts.length >= 2);
      moonOrbitMat.opacity = isNight ? 0.42 : 0.22;
    }

    // 2. 太陽・月の配置
    currentRayMode = 'off';
    if (isAnime) {
      baseSunScale = 3.1;
      baseMoonScale = 2.9;
      if (!isNight) {
        const dayStep = moment.phase * 5 + moment.subStep;
        const dayProgress = dayStep / 14;
        const az = (96 + dayProgress * 168) * RAD;
        const alt = (0.10 + Math.sin(dayProgress * Math.PI) * 0.72);
        const sVec = altAzToVec3(alt, az, SKY_R - 0.8);
        const sunMood = moment.phase === 0 ? 'energetic' : (moment.phase === 1 ? 'calm' : 'sleepy');
        sunSprite.visible = true;
        sunSprite.position.copy(sVec);
        sunSpriteMat.map = texSunAnime[sunMood];
        sunSpriteMat.opacity = 1.0;
        sunSpriteMat.needsUpdate = true;

        sunRaySprite.position.copy(sVec).multiplyScalar(1.008);
        if (moment.phase === 0) {
          currentRayMode = 'morning'; sunRaySprite.visible = true; sunRayMat.opacity = 0.95;
        } else if (moment.phase === 1) {
          currentRayMode = 'noon'; sunRaySprite.visible = true; sunRayMat.opacity = 0.48;
        } else {
          sunRaySprite.visible = false;
        }
        moonSprite.visible = false;
      } else {
        sunSprite.visible = false;
        sunRaySprite.visible = false;
        const nightStep = (moment.phase - 3) * 5 + moment.subStep;
        const nightProgress = nightStep / 9;
        const az = (120 + nightProgress * 120) * RAD;
        const alt = (0.18 + Math.sin(nightProgress * Math.PI) * 0.52);
        const shape = (curMoon.illum <= 0.55) ? 'crescent' : 'full';
        moonSprite.visible = true;
        moonSprite.position.copy(altAzToVec3(alt, az, SKY_R - 1.0));
        moonSpriteMat.map = texMoonAnime[shape][moment.moonMood] || texMoonAnime[shape].calm;
        moonSpriteMat.opacity = moment.moonMood === 'sleepy' ? 0.88 : 1.0;
        moonSpriteMat.needsUpdate = true;
      }
    } else {
      sunRaySprite.visible = false;
      baseSunScale = isPreObs ? 2.15 : 1.65;
      baseMoonScale = isPreObs ? 1.70 : 1.45;

      if (!isNight && sunPos.alt > -0.04) {
        const sVec = altAzToVec3(Math.max(0.05, sunPos.alt), sunPos.az, SKY_R - 0.8);
        const horizonFade = Math.min(1, Math.max(0.55, (sunPos.alt + 0.04) / 0.16));
        sunSprite.visible = true;
        sunSprite.position.copy(sVec);
        if (isPreObs) {
          sunSpriteMat.map = texSunReal; sunSpriteMat.opacity = 0.98 * horizonFade;
        } else {
          sunSpriteMat.map = texSunObs;  sunSpriteMat.opacity = 0.92;
        }
        sunSpriteMat.needsUpdate = true;
      } else {
        sunSprite.visible = false;
      }

      if (moonPos.alt > -0.02) {
        moonSprite.visible = true;
        moonSprite.position.copy(altAzToVec3(Math.max(0.04, moonPos.alt), moonPos.az, SKY_R - 1.2));
        if (isPreObs) {
          updateRealMoonTexture(curMoon.phaseDeg);
          moonSpriteMat.map = texMoonReal; moonSpriteMat.opacity = isNight ? 0.98 : 0.62;
        } else {
          moonSpriteMat.map = texMoonObs;  moonSpriteMat.opacity = isNight ? 0.92 : 0.58;
        }
        moonSpriteMat.needsUpdate = true;
      } else {
        moonSprite.visible = false;
      }
    }

    // 3. 星座（夜〜深夜のみ表示）
    starGroup.visible = isNight;
    baseStarSize = isAnime ? 1.25 : 0.82;
    if (isNight) {
      const pArr = [], lArr = [];
      CONSTELLATIONS.forEach((con) => {
        const mapped = con.stars.map(([raH, decD]) => {
          const { alt, az } = raDecToAltAz(raH * 15, decD, curLst, state.lat);
          return { alt, vec: altAzToVec3(alt, az, STAR_R) };
        });
        mapped.forEach((st) => { if (st.alt > 0.02) pArr.push(st.vec.x, st.vec.y, st.vec.z); });
        con.lines.forEach(([a, b]) => {
          if (mapped[a] && mapped[b] && mapped[a].alt > 0.01 && mapped[b].alt > 0.01) {
            lArr.push(mapped[a].vec.x, mapped[a].vec.y, mapped[a].vec.z, mapped[b].vec.x, mapped[b].vec.y, mapped[b].vec.z);
          }
        });
      });
      starPoints.geometry.dispose();
      starPoints.geometry = new THREE.BufferGeometry();
      starPoints.geometry.setAttribute('position', new THREE.Float32BufferAttribute(pArr, 3));
      starPointsMat.map = isAnime ? texStarCirclePastel : texStarCircleDigital;
      starPointsMat.color.setHex(isAnime ? 0xfffbeb : 0xe0f2fe);
      starPointsMat.needsUpdate = true;

      starLines.geometry.dispose();
      starLines.geometry = new THREE.BufferGeometry();
      starLines.geometry.setAttribute('position', new THREE.Float32BufferAttribute(lArr, 3));
      if (isAnime) { starLines.material = starLineDashMat; starLines.computeLineDistances(); starLines.visible = true; }
      else if (isPreObs) { starLines.material = starLineSolidMat; starLines.visible = true; }
      else { starLines.material = starLineSolidMat; starLines.visible = Boolean(state.obsConstLines); }
    }

    applyDynamicCameraScales(performance.now() * 0.001);

    const mNames = { anime: '①ゲーム', pre_obs: '②天体観察前', obs: '③天体観察時' };
    const turnProg = isObs ? `現在時刻(太陽高度 ${(sunPos.alt * DEG).toFixed(0)}°)` : `${moment.turn}T目・${PHASE_LABELS[moment.phase]}(${moment.subStep + 1}/5)`;
    const gpsTag = state.gpsApplied ? `GPS(${state.lat.toFixed(2)}°N, ${state.lon.toFixed(2)}°E)` : '日本標準(35.7°N)';
    lastStatusText = `${mNames[state.viewMode]}｜${turnProg}｜日出${formatHM(moment.times.sunriseH)}・日入${formatHM(moment.times.sunsetH)}｜${gpsTag}`;
    syncSettingsUI();
  }

  // --- 純正GPSリクエスト（Android Kotlin側へ要求） ---
  function triggerNativeGpsRequest() {
    const bridge = window.AndroidCompass;
    if (bridge && typeof bridge.requestGps === 'function') {
      bridge.requestGps();
      if (typeof showToast === 'function') showToast('📍 Android純正GPSで現在地を取得中...');
      setTimeout(() => {
        pollNativeBridge();
        updateCelestialExhibit(true);
      }, 1200);
    } else if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          state.lat = Number(pos.coords.latitude.toFixed(4));
          state.lon = Number(pos.coords.longitude.toFixed(4));
          state.gpsApplied = true;
          saveState();
          updateCelestialExhibit(true);
          if (typeof showToast === 'function') showToast(`📍 現在地の空へ同期しました (${state.lat.toFixed(2)}°N)`);
        },
        () => {
          if (typeof showToast === 'function') showToast('📍 APKアプリ内で実行すると純正GPSが自動連動します');
        },
        { enableHighAccuracy: false, timeout: 6000 }
      );
    }
  }

  // --- 設定ページ「🔭 観察・空」タブ内UI統合 ---
  function syncSettingsUI() {
    const obsPanel = document.getElementById('settings-tab-panel-obs');
    if (!obsPanel) return;

    const placeholder = document.getElementById('settings-obs-sky-placeholder');
    if (placeholder) placeholder.style.display = 'none';

    let box = document.getElementById('env-skytime-unified-box');
    if (!box) {
      box = document.createElement('div');
      box.id = 'env-skytime-unified-box';
      box.style.cssText = 'display:flex;flex-direction:column;gap:8px;text-align:left;';
      box.innerHTML = `
        <!-- 1. 3モード切替 -->
        <div style="background:#f0f9ff;border:1.5px solid #7dd3fc;border-radius:11px;padding:8px 9px;display:flex;flex-direction:column;gap:6px;">
          <div style="font-size:11px;font-weight:900;color:#0369a1;">🔭 観察モード（二重外スクリーン＆連動切替）</div>
          <div style="display:flex;gap:4px;">
            <button type="button" data-skymode="anime" style="flex:1;padding:7px 4px;border-radius:8px;border:1px solid #bae6fd;font-size:10.5px;font-weight:900;cursor:pointer;">① ゲーム</button>
            <button type="button" data-skymode="pre_obs" style="flex:1;padding:7px 4px;border-radius:8px;border:1px solid #bae6fd;font-size:10.5px;font-weight:900;cursor:pointer;">② 天体観察前</button>
            <button type="button" data-skymode="obs" style="flex:1;padding:7px 4px;border-radius:8px;border:1px solid #bae6fd;font-size:10.5px;font-weight:900;cursor:pointer;">③ 天体観察時</button>
          </div>
          <div id="sky-mode-rule-hint" style="font-size:9.5px;font-weight:700;color:#0369a1;line-height:1.4;"></div>
        </div>

        <!-- 2. ②天体観察前 ハイブリッド切替 -->
        <div id="row-preobs-hybrid" style="background:#fffbeb;border:1.5px solid #fcd34d;border-radius:11px;padding:8px 9px;display:none;flex-direction:column;gap:6px;">
          <div style="font-size:11px;font-weight:900;color:#92400e;">🔀 ②天体観察前：コンパスのハイブリッド切替</div>
          <div style="display:flex;gap:5px;">
            <button type="button" data-hybrid="spin" style="flex:1;padding:7px 6px;border-radius:8px;border:1px solid #fcd34d;font-size:10.5px;font-weight:900;cursor:pointer;">🔄 くるくる回転</button>
            <button type="button" data-hybrid="native" style="flex:1;padding:7px 6px;border-radius:8px;border:1px solid #fcd34d;font-size:10.5px;font-weight:900;cursor:pointer;">🧭 実機コンパス連動</button>
          </div>
        </div>

        <!-- 3. コンパス構造切替（高精度 vs 子ども向け磁石吸着） -->
        <div style="background:#f8fafc;border:1.5px solid #cbd5e1;border-radius:11px;padding:8px 9px;display:flex;flex-direction:column;gap:6px;">
          <div style="font-size:11px;font-weight:900;color:#0f172a;">🧭 実機コンパスの構造切替</div>
          <div style="display:flex;gap:5px;">
            <button type="button" data-compstyle="precision" style="flex:1;padding:7px 5px;border-radius:8px;border:1px solid #cbd5e1;font-size:10.5px;font-weight:900;cursor:pointer;">🔬 高精度コンパス<div style="font-size:9px;font-weight:600;opacity:0.85;">1°単位の正確計測</div></button>
            <button type="button" data-compstyle="kid" style="flex:1;padding:7px 5px;border-radius:8px;border:1px solid #cbd5e1;font-size:10.5px;font-weight:900;cursor:pointer;">🧭 子ども向けコンパス<div style="font-size:9px;font-weight:600;opacity:0.85;">東西南北に磁石吸着</div></button>
          </div>

          <div style="font-size:10px;font-weight:800;color:#475569;margin-top:2px;">📱 スマホの持ち方基準</div>
          <div style="display:flex;gap:4px;">
            <button type="button" data-posture="0" style="flex:1;padding:5px 4px;border-radius:7px;border:1px solid #cbd5e1;font-size:10px;font-weight:800;cursor:pointer;">🔄 自動判定</button>
            <button type="button" data-posture="1" style="flex:1;padding:5px 4px;border-radius:7px;border:1px solid #cbd5e1;font-size:10px;font-weight:800;cursor:pointer;">🛌 寝かせ持ち</button>
            <button type="button" data-posture="2" style="flex:1;padding:5px 4px;border-radius:7px;border:1px solid #cbd5e1;font-size:10px;font-weight:800;cursor:pointer;">📱 立て持ち</button>
          </div>
        </div>

        <!-- 4. ホログラム輪っか & 星座線表示 -->
        <div style="background:#ffffff;border:1px solid #e2e8f0;border-radius:10px;padding:7px 9px;display:flex;flex-direction:column;gap:6px;">
          <div style="display:flex;align-items:center;justify-content:space-between;gap:8px;">
            <span style="font-size:10.5px;font-weight:800;color:#0f172a;">💠 3Dホログラムコンパス表示</span>
            <button type="button" id="btn-obs-tab-holo-toggle" style="padding:4px 10px;border-radius:7px;border:none;font-size:10px;font-weight:900;cursor:pointer;"></button>
          </div>
          <div id="row-obs-const-line" style="display:flex;align-items:center;justify-content:space-between;gap:8px;padding-top:5px;border-top:1px dashed #e2e8f0;">
            <span style="font-size:10.5px;font-weight:800;color:#334155;">✨ ③観察時の星座線（夜・深夜）</span>
            <button type="button" id="btn-toggle-obs-lines" style="padding:4px 10px;border-radius:7px;border:none;font-size:10px;font-weight:900;cursor:pointer;"></button>
          </div>
        </div>

        <!-- 5. GPS同期 & ステータス -->
        <div style="display:flex;align-items:center;gap:6px;">
          <button type="button" id="btn-gps-sync-sky" style="flex:1;padding:7px 8px;border-radius:8px;border:1px solid #38bdf8;background:#e0f2fe;color:#0369a1;font-size:10.5px;font-weight:900;cursor:pointer;">📍 GPSで現在地の空に合わせる</button>
          <button type="button" id="btn-gps-reset-sky" style="padding:7px 8px;border-radius:8px;border:1px solid #cbd5e1;background:#ffffff;color:#475569;font-size:10px;font-weight:800;cursor:pointer;">日本標準</button>
        </div>
        <div id="env-skytime-status" style="font-size:9.5px;font-weight:700;color:#0369a1;background:#e0f2fe;border-radius:7px;padding:6px 8px;line-height:1.45;"></div>
      `;
      obsPanel.insertBefore(box, obsPanel.firstChild);

      box.querySelectorAll('[data-skymode]').forEach((btn) => {
        btn.onclick = () => {
          state.viewMode = btn.getAttribute('data-skymode');
          nativeBridge.userPausedTracking = false;
          saveState();
          updateCelestialExhibit(true);
        };
      });

      box.querySelectorAll('[data-hybrid]').forEach((btn) => {
        btn.onclick = () => {
          state.preObsHybrid = btn.getAttribute('data-hybrid');
          nativeBridge.userPausedTracking = false;
          saveState();
          updateCelestialExhibit(true);
        };
      });

      box.querySelectorAll('[data-compstyle]').forEach((btn) => {
        btn.onclick = () => {
          state.compassStyle = btn.getAttribute('data-compstyle');
          saveState();
          updateCelestialExhibit(true);
        };
      });

      box.querySelectorAll('[data-posture]').forEach((btn) => {
        btn.onclick = () => {
          state.postureMode = Number(btn.getAttribute('data-posture'));
          saveState();
          if (window.AndroidCompass && typeof window.AndroidCompass.setPostureMode === 'function') {
            window.AndroidCompass.setPostureMode(state.postureMode);
          }
          syncSettingsUI();
        };
      });

      document.getElementById('btn-obs-tab-holo-toggle').onclick = () => {
        const compMod = window.CompassRingModule;
        if (compMod && typeof compMod.toggleVisible === 'function') {
          compMod.toggleVisible();
          syncSettingsUI();
        }
      };

      document.getElementById('btn-toggle-obs-lines').onclick = () => {
        state.obsConstLines = !state.obsConstLines;
        saveState();
        updateCelestialExhibit(true);
      };

      document.getElementById('btn-gps-sync-sky').onclick = triggerNativeGpsRequest;
      document.getElementById('btn-gps-reset-sky').onclick = () => {
        state.lat = 35.68; state.lon = 139.77; state.gpsApplied = false;
        saveState();
        updateCelestialExhibit(true);
        if (typeof showToast === 'function') showToast('📍 日本標準座標(35.7°N)へ戻しました');
      };
    }

    // 選択状態のビジュアル反映
    box.querySelectorAll('[data-skymode]').forEach((btn) => {
      const sel = (btn.getAttribute('data-skymode') === state.viewMode);
      btn.style.background = sel ? '#0284c7' : '#ffffff';
      btn.style.color = sel ? '#ffffff' : '#334155';
    });

    const ruleHint = document.getElementById('sky-mode-rule-hint');
    if (ruleHint) {
      if (state.viewMode === 'anime') {
        ruleHint.textContent = '・①ゲーム：外スクリーンはアニメ空、コンパス輪はくるくる回転します。';
      } else if (state.viewMode === 'pre_obs') {
        ruleHint.textContent = '・②天体観察前：外スクリーンで軌道と天体を予習。下のハイブリッド切替でコンパス動作を選べます。';
      } else {
        ruleHint.textContent = '・③天体観察時：GPSと現在時刻のリアル星空／朝日／夕焼けを表示し、実機コンパス連動に固定（暗転停止）します。';
      }
    }

    const hybridRow = document.getElementById('row-preobs-hybrid');
    if (hybridRow) hybridRow.style.display = (state.viewMode === 'pre_obs') ? 'flex' : 'none';
    box.querySelectorAll('[data-hybrid]').forEach((btn) => {
      const sel = (btn.getAttribute('data-hybrid') === state.preObsHybrid);
      btn.style.background = sel ? '#d97706' : '#ffffff';
      btn.style.color = sel ? '#ffffff' : '#78350f';
    });

    box.querySelectorAll('[data-compstyle]').forEach((btn) => {
      const sel = (btn.getAttribute('data-compstyle') === state.compassStyle);
      btn.style.background = sel ? '#0f172a' : '#ffffff';
      btn.style.color = sel ? '#ffffff' : '#1e293b';
      btn.style.borderColor = sel ? '#0284c7' : '#cbd5e1';
    });

    box.querySelectorAll('[data-posture]').forEach((btn) => {
      const sel = (Number(btn.getAttribute('data-posture')) === state.postureMode);
      btn.style.background = sel ? '#0284c7' : '#ffffff';
      btn.style.color = sel ? '#ffffff' : '#334155';
    });

    const holoBtn = document.getElementById('btn-obs-tab-holo-toggle');
    if (holoBtn) {
      const compMod = window.CompassRingModule;
      const holoOn = (compMod && typeof compMod.isUserEnabled === 'function') ? compMod.isUserEnabled() : false;
      holoBtn.textContent = holoOn ? '表示中 (ON)' : '非表示 (OFF)';
      holoBtn.style.background = holoOn ? '#0284c7' : '#e2e8f0';
      holoBtn.style.color = holoOn ? '#ffffff' : '#475569';
    }

    const rLn = document.getElementById('row-obs-const-line');
    const bLn = document.getElementById('btn-toggle-obs-lines');
    if (rLn) rLn.style.display = (state.viewMode === 'obs') ? 'flex' : 'none';
    if (bLn) {
      bLn.textContent = state.obsConstLines ? '星座線 ON' : '星座線 OFF';
      bLn.style.background = state.obsConstLines ? '#0284c7' : '#e2e8f0';
      bLn.style.color = state.obsConstLines ? '#ffffff' : '#475569';
    }

    const stEl = document.getElementById('env-skytime-status');
    if (stEl) {
      const connTxt = nativeBridge.connected ? '✅ Android実機センサー接続済み' : '🌐 ブラウザ表示（APKで実機連動）';
      stEl.textContent = `${connTxt}\n${lastStatusText}`;
    }
  }

  // --- 毎フレーム監視 ---
  function animateSky(now) {
    hideLegacySkyObjects();
    updateCompassAndCameraLoop();
    if (typeof activeCamera !== 'undefined' && activeCamera) {
      skyRoot.position.copy(activeCamera.position);
    }
    applyDynamicCameraScales(now * 0.001);
    requestAnimationFrame(animateSky);
  }

  if (typeof updateEnvironmentAndUI === 'function') {
    const _origUpdateEnv = updateEnvironmentAndUI;
    updateEnvironmentAndUI = function() {
      const r = _origUpdateEnv.apply(this, arguments);
      updateCelestialExhibit(false);
      return r;
    };
  }
  setInterval(() => {
    updateCelestialExhibit(false);
  }, 2000);
  setTimeout(() => { updateCelestialExhibit(true); syncSettingsUI(); }, 600);
  requestAnimationFrame(animateSky);

  window.SkyTimeModule = {
    setMode: (m) => { if (['anime', 'pre_obs', 'obs'].includes(m)) { state.viewMode = m; saveState(); updateCelestialExhibit(true); } },
    getMode: () => state.viewMode,
    isNativeCompassActive,
    setObsConstLines: (v) => { state.obsConstLines = Boolean(v); saveState(); updateCelestialExhibit(true); },
    advanceTurn,
    resetTurns,
    requestGPS: triggerNativeGpsRequest,
    getPhaseIndex: () => Math.min(3, resolveSkyMoment().phase),
    syncSettingsUI,
    refresh: () => updateCelestialExhibit(true)
  };

  if (window.DebugGuard) window.DebugGuard.markLoaded('sky_time.js');
})();
