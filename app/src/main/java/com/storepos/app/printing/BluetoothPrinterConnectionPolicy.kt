package com.storepos.app.printing

/**
 * Android 12 (API 31) split nearby-device permissions:
 * BLUETOOTH_CONNECT allows paired classic Bluetooth sockets, while
 * BluetoothAdapter.cancelDiscovery needs BLUETOOTH_SCAN. For a paired
 * ESC/POS printer we do not need discovery cancellation on 31+.
 */
internal fun shouldCancelLegacyDiscovery(androidSdk: Int): Boolean = androidSdk < 31
