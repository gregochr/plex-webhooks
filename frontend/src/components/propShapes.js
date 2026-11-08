import PropTypes from 'prop-types';

/** What lib/scenes.js `sceneSlot` returns. */
export const slotShape = PropTypes.shape({
  slot: PropTypes.oneOf(['start', 'stop']).isRequired,
  id: PropTypes.string,
  label: PropTypes.string.isRequired,
  level: PropTypes.number,
  next: PropTypes.string,
  disabledReason: PropTypes.string,
});
