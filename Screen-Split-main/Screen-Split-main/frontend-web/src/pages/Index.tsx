import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { store } from "@/lib/mockStore";
import { useStore } from "@/hooks/useStore";

const Index = () => {
  const nav = useNavigate();
  const me = useStore(() => store.currentUser());

  useEffect(() => {
    nav(me ? "/groups" : "/welcome", { replace: true });
  }, [me, nav]);

  return (
    <div className="min-h-screen grid place-items-center bg-background">
      <div className="font-display text-2xl animate-pulse">ScreenSplit</div>
    </div>
  );
};

export default Index;
