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

// Close nav on link click
document.querySelectorAll('.nav-links a').forEach(a => {
  a.addEventListener('click', () => navLinks.classList.remove('open'));
});

// Close nav on scroll
let lastScroll = 0;
window.addEventListener('scroll', () => {
  if (window.scrollY > lastScroll) navLinks.classList.remove('open');
  lastScroll = window.scrollY;
});

// Lightbox
const lightbox = document.getElementById('lightbox');
const lbImg = document.getElementById('lbImg');
const lbClose = document.getElementById('lbClose');

document.querySelectorAll('.ss-item').forEach(item => {
  item.addEventListener('click', e => {
    e.preventDefault();
    const href = item.getAttribute('href');
    lbImg.setAttribute('src', href);
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
