function MinimizeIcon(): JSX.Element {
  return (
    <svg viewBox="0 0 10 10" fill="none" stroke="currentColor" strokeWidth={1.2} aria-hidden="true">
      <line x1={1} y1={5} x2={9} y2={5} />
    </svg>
  )
}

function MaximizeIcon(): JSX.Element {
  return (
    <svg viewBox="0 0 10 10" fill="none" stroke="currentColor" strokeWidth={1.2} aria-hidden="true">
      <rect x={1.5} y={1.5} width={7} height={7} />
    </svg>
  )
}

function CloseIcon(): JSX.Element {
  return (
    <svg viewBox="0 0 10 10" fill="none" stroke="currentColor" strokeWidth={1.2} aria-hidden="true">
      <line x1={1} y1={1} x2={9} y2={9} />
      <line x1={9} y1={1} x2={1} y2={9} />
    </svg>
  )
}

export function TitleBar(): JSX.Element {
  const handleMinimize = (): void => {
    void window.api.windowControls.minimize()
  }

  const handleMaximizeToggle = (): void => {
    void window.api.windowControls.maximizeToggle()
  }

  const handleClose = (): void => {
    void window.api.windowControls.close()
  }

  return (
    <div className="titlebar">
      <div className="titlebar-brand">
        <span className="dot" />
        Cinematic Weather
      </div>
      <div className="window-controls">
        <button type="button" className="window-btn" aria-label="Minimize" onClick={handleMinimize}>
          <MinimizeIcon />
        </button>
        <button type="button" className="window-btn" aria-label="Maximize" onClick={handleMaximizeToggle}>
          <MaximizeIcon />
        </button>
        <button type="button" className="window-btn close" aria-label="Close" onClick={handleClose}>
          <CloseIcon />
        </button>
      </div>
    </div>
  )
}
