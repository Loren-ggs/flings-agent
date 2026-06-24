import { RouterProvider } from "react-router-dom";

import { ErrorBoundary } from "@/components/common/ErrorBoundary";
import { Toast } from "@/components/common/Toast";
import { router } from "@/router";
import { FeatureWorkspace } from "@/components/workspace/FeatureWorkspace";

export default function App() {
  return (
    <ErrorBoundary>
      <RouterProvider router={router} />
      <FeatureWorkspace />
      <Toast />
    </ErrorBoundary>
  );
}
