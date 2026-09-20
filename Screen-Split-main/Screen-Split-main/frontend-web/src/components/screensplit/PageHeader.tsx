import { ArrowLeft } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { cn } from "@/lib/utils";

interface Props {
  title: string;
  subtitle?: string;
  back?: boolean;
  right?: React.ReactNode;
  className?: string;
}

export function PageHeader({ title, subtitle, back, right, className }: Props) {
  const nav = useNavigate();
  return (
    <header className={cn("flex items-start justify-between gap-3 pt-6 pb-4", className)}>
      <div className="flex items-start gap-2 min-w-0">
        {back && (
          <button
            onClick={() => nav(-1)}
            aria-label="Back"
            className="mt-1 grid place-items-center h-9 w-9 rounded-full border-2 border-foreground bg-card hover:bg-accent transition-colors"
          >
            <ArrowLeft className="h-4 w-4" strokeWidth={2.5} />
          </button>
        )}
        <div className="min-w-0">
          <h1 className="font-display text-3xl font-bold leading-none truncate">{title}</h1>
          {subtitle && <p className="text-sm text-muted-foreground mt-1">{subtitle}</p>}
        </div>
      </div>
      {right}
    </header>
  );
}
