// AOS init
AOS.init({
  duration: 600,
  easing: 'ease-out-cubic',
  once: true,
  offset: 40,
});

// Nav toggle
const navToggle = document.getElementById('navToggle');
const navLinks = document.querySelector('.nav-links');

navToggle.addEventListener('click', () => {
  navLinks.classList.toggle('open');
});

document.querySelectorAll('.nav-links a').forEach(a => {
  a.addEventListener('click', () => navLinks.classList.remove('open'));
});

let lastScroll = 0;
window.addEventListener('scroll', () => {
  if (window.scrollY > lastScroll) navLinks.classList.remove('open');
  lastScroll = window.scrollY;
  // Nav shadow on scroll
  document.getElementById('nav').classList.toggle('nav-scrolled', window.scrollY > 20);
});

// Lightbox
const lightbox = document.getElementById('lightbox');
const lbImg = document.getElementById('lbImg');
const lbClose = document.getElementById('lbClose');

document.querySelectorAll('.ss-item').forEach(item => {
  item.addEventListener('click', e => {
    e.preventDefault();
    lbImg.src = item.getAttribute('href');
    lightbox.classList.add('active');
    document.body.style.overflow = 'hidden';
  });
});

function closeLightbox() {
  lightbox.classList.remove('active');
  document.body.style.overflow = '';
}

lbClose.addEventListener('click', closeLightbox);
lightbox.addEventListener('click', e => {
  if (e.target === lightbox) closeLightbox();
});
document.addEventListener('keydown', e => {
  if (e.key === 'Escape') closeLightbox();
});

// Year
document.getElementById('year').textContent = new Date().getFullYear();

// ============================================================
// GitHub API — fetch latest release & populate download links
// ============================================================
(async function fetchLatestRelease() {
  const owner = 'wiqis';
  const repo = 'kdm';
  const cacheKey = `kdm_latest_release`;
  const cacheTTL = 30 * 60 * 1000; // 30 minutes

  // Try session cache
  const cached = sessionStorage.getItem(cacheKey);
  if (cached) {
    try {
      const data = JSON.parse(cached);
      if (Date.now() - data.ts < cacheTTL) {
        populateDownloads(data.release);
        return;
      }
    } catch (_) {}
  }

  try {
    const res = await fetch(`https://api.github.com/repos/${owner}/${repo}/releases/latest`, {
      headers: { Accept: 'application/vnd.github.v3+json' },
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const release = await res.json();

    // Cache
    sessionStorage.setItem(cacheKey, JSON.stringify({ ts: Date.now(), release }));

    populateDownloads(release);
  } catch (err) {
    console.warn('Failed to fetch latest release:', err);
    // Fallback to latest release page
    document.querySelectorAll('.download-card').forEach(el => {
      if (el.id && el.id.startsWith('dl')) {
        el.href = `https://github.com/${owner}/${repo}/releases/latest`;
      }
    });
  }
})();

function populateDownloads(release) {
  const version = release.tag_name || release.name || '';
  const assets = release.assets || [];

  const links = { windows: '', linux: '', mac: '' };

  for (const a of assets) {
    const name = a.name.toLowerCase();
    const url = a.browser_download_url;
    if (!url) continue;

    if (name.endsWith('.msi') && !links.windows) {
      links.windows = url;
    } else if (name.endsWith('.deb') && !links.linux) {
      links.linux = url;
    } else if (name.endsWith('.dmg') && !links.mac) {
      links.mac = url;
    }
    // Fallback: if no .msi, try .exe
    if (!links.windows && name.endsWith('.exe')) {
      links.windows = url;
    }
  }

  const setLink = (id, url) => {
    const el = document.getElementById(id);
    if (el && url) el.href = url;
  };

  setLink('dlWindows', links.windows);
  setLink('dlLinux', links.linux);
  setLink('dlMac', links.mac);

  // Version badge
  const verEl = document.getElementById('dlVersion');
  if (verEl && version) {
    verEl.textContent = version;
  }

  // Download count in header button
  if (release.assets_download_count > 0 || release.download_count > 0) {
    const count = release.assets_download_count || release.download_count || 0;
    const countEl = document.getElementById('headerDownloadCount');
    if (countEl) {
      countEl.textContent = formatCount(count) + ' downloads';
    }
  }
}

function formatCount(n) {
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1).replace(/\.0$/, '') + 'M+';
  if (n >= 1_000) return (n / 1_000).toFixed(1).replace(/\.0$/, '') + 'K+';
  return n.toString();
}

// ============================================================
// Animated counter (stats section)
// ============================================================
const counters = document.querySelectorAll('.stat-count');

const counterObserver = new IntersectionObserver(entries => {
  for (const entry of entries) {
    if (entry.isIntersecting) {
      const el = entry.target;
      const target = parseInt(el.dataset.target, 10);
      if (isNaN(target)) continue;
      animateCounter(el, target);
      counterObserver.unobserve(el);
    }
  }
}, { threshold: 0.5 });

counters.forEach(c => counterObserver.observe(c));

function animateCounter(el, target) {
  const duration = 1500;
  const start = performance.now();

  function tick(now) {
    const elapsed = now - start;
    const progress = Math.min(elapsed / duration, 1);
    // Ease out quad
    const eased = 1 - (1 - progress) * (1 - progress);
    const current = Math.round(eased * target);

    // Handle number formatting
    if (target >= 1000) {
      el.textContent = current.toLocaleString();
    } else {
      el.textContent = current;
    }

    if (progress < 1) {
      requestAnimationFrame(tick);
    } else {
      if (target >= 1000) {
        el.textContent = target.toLocaleString() + '+';
      } else {
        el.textContent = target;
      }
    }
  }

  requestAnimationFrame(tick);
}
