/// <reference types="vite/client" />

/** Injected by the ScreenSplit Android app WebView (see OAuthJsBridge). */
interface ScreenSplitNativeBridge {
  openOAuthUrl: (url: string) => void;
}

declare global {
  interface Window {
    ScreenSplitNative?: ScreenSplitNativeBridge;
  }
}

export {};
