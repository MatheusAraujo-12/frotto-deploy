import type { PointerEventHandler } from "react";

declare module "react" {
  interface DOMAttributes<T> {
    onPointerEnterCapture?: PointerEventHandler<T>;
    onPointerLeaveCapture?: PointerEventHandler<T>;
  }

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  interface HTMLAttributes<T> {
    placeholder?: string;
  }
}

export {};
