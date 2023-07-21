package com.android.internal.gmscompat.gcarriersettings;

import android.os.Bundle;
import android.service.carrier.CarrierIdentifier;

interface ICarrierConfigsLoader {
    Bundle getConfigs(in CarrierIdentifier carrierId);
}
