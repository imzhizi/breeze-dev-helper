(function() {
  // Use the injected scheme from window.__breezeJumpScheme (set by the Java side),
  // or fall back to the default if not available.
  const defaultScheme = 'breeze-jump://';
  const eventName = 'breezeJumpOpenLink';

  function getScheme() {
    return (window.__breezeJumpScheme && window.__breezeJumpScheme.length > 0)
        ? window.__breezeJumpScheme
        : defaultScheme;
  }

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
    if (!href || !href.startsWith(getScheme())) {
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