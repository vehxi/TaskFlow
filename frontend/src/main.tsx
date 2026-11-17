import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import { App } from './app/App.tsx';
import { SessionProvider } from './app/session.tsx';
import './app/base.css';
import { ThemeProvider } from './app/theme.tsx';

createRoot(document.getElementById('root')!).render(
  <StrictMode><ThemeProvider><BrowserRouter><SessionProvider><App /></SessionProvider></BrowserRouter></ThemeProvider></StrictMode>,
);
