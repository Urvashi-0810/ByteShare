import { createRoot } from "react-dom/client";
import App from "./App.tsx";
import { initKeyboardViewportHelpers } from "./lib/keyboardViewport";
import "./index.css";

initKeyboardViewportHelpers();

createRoot(document.getElementById("root")!).render(<App />);
