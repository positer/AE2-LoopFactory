package com.example.ae2lightoptimizer.factory;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;

public interface FactoryEditorHost {
    default String editorTitleKey() { return "block.ae2lightoptimizer.loop_factory_network_terminal"; }
    InternalInventory factoryPatterns();
    String factoryDraft();
    void setFactoryDraft(String draft);
    IGrid editorGrid();
    default FactoryBlockEntity editingFactory() { return FactoryServer.owner(editorGrid()); }
    default boolean allowsRecipe() { return true; }
}
