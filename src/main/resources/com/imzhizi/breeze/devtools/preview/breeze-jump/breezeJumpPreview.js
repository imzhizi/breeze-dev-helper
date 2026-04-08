(function() {
  const scheme = 'breeze-jump://';
  const eventName = 'breezeJumpOpenLink';

  function findAnchor(target) {
    if (!target) {
      return null;
    }
    return target.closest ? target.closest('a[href]') : null;
  }

  function handleClick(event) {
    const anchor = findAnchor(event.target);
    if (!anchor) {
      return;
    }

    const href = anchor.getAttribute('href');
    if (!href || !href.startsWith(scheme)) {
      return;
    }

    event.preventDefault();
    event.stopPropagation();

    const pipe = window.__IntelliJTools && window.__IntelliJTools.messagePipe;
    if (pipe) {
      pipe.post(eventName, href);
    }
  }

  document.addEventListener('click', handleClick, true);
})();