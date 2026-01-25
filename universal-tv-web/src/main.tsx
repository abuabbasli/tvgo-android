import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import './index.css'
import { registerTizenKeys } from './utils/tizenKeys'

// Initialize Tizen TV remote control keys
registerTizenKeys();

ReactDOM.createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
        <App />
    </React.StrictMode>,
)

