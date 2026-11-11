package net.alkahest.loot;

/** Transient per-victim guard; active only while normal mob loot and equipment are dropping. */
public interface MidasLootState {
    boolean alkahest$duplicateDrops();
    boolean alkahest$copyingDrop();
    void alkahest$setCopyingDrop(boolean copying);
}
