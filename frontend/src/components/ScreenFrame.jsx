import { useEffect, useRef } from 'react';
import PropTypes from 'prop-types';

const ROOM = '#';

/** The frame of a More-menu screen: a "Back to the room" link, a heading that takes focus on arrival, a lede. */
export default function ScreenFrame({ title, lede, children }) {
  const heading = useRef(null);
  useEffect(() => {
    heading.current?.focus({ preventScroll: true });
  }, []);

  return (
    <section className="screen" aria-labelledby="screen-title">
      <a className="screen-back" href={ROOM}>
        Back to the room
      </a>
      <h2 id="screen-title" className="screen-title" tabIndex={-1} ref={heading}>
        {title}
      </h2>
      {lede && <p className="screen-lede">{lede}</p>}
      {children}
    </section>
  );
}

ScreenFrame.propTypes = {
  title: PropTypes.string.isRequired,
  lede: PropTypes.string,
  children: PropTypes.node,
};
