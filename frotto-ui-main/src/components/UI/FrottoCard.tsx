import { IonCard } from "@ionic/react";
import React from "react";
import "./FrottoCard.css";

type IonCardProps = React.ComponentProps<typeof IonCard>;

export interface FrottoCardProps extends IonCardProps {
  /**
   * Marks the card as clickable: adds hover/focus affordance and, when an
   * onClick is also provided, accessible button semantics (role, tabIndex,
   * Enter/Space activation) — without turning the card into a real <button>.
   */
  interactive?: boolean;
}

const FrottoCard: React.FC<FrottoCardProps> = ({
  interactive = false,
  className,
  onClick,
  onKeyDown,
  children,
  ...rest
}) => {
  const clickable = interactive && Boolean(onClick);

  const handleKeyDown: IonCardProps["onKeyDown"] = (event) => {
    if (clickable && (event.key === "Enter" || event.key === " ")) {
      event.preventDefault();
      (onClick as React.MouseEventHandler)(event as unknown as React.MouseEvent);
    }
    onKeyDown?.(event);
  };

  return (
    <IonCard
      className={[
        "frotto-card",
        "app-panel-card",
        interactive ? "frotto-card--interactive" : "",
        className,
      ]
        .filter(Boolean)
        .join(" ")}
      onClick={onClick}
      onKeyDown={clickable ? handleKeyDown : onKeyDown}
      role={clickable ? "button" : undefined}
      tabIndex={clickable ? 0 : undefined}
      {...rest}
    >
      {children}
    </IonCard>
  );
};

export default FrottoCard;
