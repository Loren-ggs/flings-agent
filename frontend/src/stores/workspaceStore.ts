import { create } from "zustand";

interface WorkspaceState {
  activeFeature: string | null;
  openFeature: (feature: string) => void;
  closeFeature: () => void;
}

export const useWorkspaceStore = create<WorkspaceState>((set) => ({
  activeFeature: null,
  openFeature: (feature) => set({ activeFeature: feature }),
  closeFeature: () => set({ activeFeature: null })
}));
