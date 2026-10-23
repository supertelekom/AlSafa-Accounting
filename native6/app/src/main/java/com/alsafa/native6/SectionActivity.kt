package com.alsafa.native6

/**
 * Separate Android screen instance for each navigation destination.
 * Reuses the existing native View renderers while legacy visual parity is migrated.
 * Android's Activity back stack owns navigation, not in-place section toggles.
 */
class SectionActivity : MainActivity()
