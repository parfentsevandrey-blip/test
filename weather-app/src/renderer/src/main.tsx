import React from 'react'
import ReactDOM from 'react-dom/client'
import '@fontsource-variable/inter'
import '@fontsource-variable/sora'
// Design-system CSS must be bundled BEFORE the component tree's co-located
// stylesheets, so component rules win ties against global defaults.
import './styles/global.css'
import { App } from './App'

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
)
