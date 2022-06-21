package vendor.google.wireless_charger;

interface IWirelessCharger {

    boolean isRtxSupported() = 12;
    void setRtxMode(boolean isSupported) = 19;
    boolean  isRtxModeOn() = 11;
}