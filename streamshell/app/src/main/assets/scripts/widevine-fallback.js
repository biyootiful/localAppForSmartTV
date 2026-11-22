// Android WebView offers Widevine only at its software level (SW_SECURE_CRYPTO) and refuses a
// request that asks for more. Desktop players ask for SW_SECURE_DECODE first, as desktop Chrome
// grants it, and stop when refused. When a Widevine request is refused, ask again at the level
// WebView does offer. Hardware levels are left alone: WebView grants none of them.
(() => {
    const proto = self.Navigator && Navigator.prototype;
    const original = proto && proto.requestMediaKeySystemAccess;
    if (!original || original.streamshell) return;

    const lower = capabilities => !Array.isArray(capabilities) ? capabilities : capabilities.map(
        c => c && c.robustness === 'SW_SECURE_DECODE' ? { ...c, robustness: 'SW_SECURE_CRYPTO' } : c);

    const asksForDecode = configs => configs.some(c => c && [c.videoCapabilities, c.audioCapabilities].some(
        caps => Array.isArray(caps) && caps.some(cap => cap && cap.robustness === 'SW_SECURE_DECODE')));

    function requestMediaKeySystemAccess(keySystem, configs) {
        const request = original.call(this, keySystem, configs);
        if (!String(keySystem).startsWith('com.widevine.alpha') || !Array.isArray(configs)) return request;
        return request.catch(error => {
            if (!error || error.name !== 'NotSupportedError' || !asksForDecode(configs)) throw error;
            console.info('[streamshell] Widevine request refused; retrying at software level');
            return original.call(this, keySystem, configs.map(c => !c ? c : {
                ...c,
                videoCapabilities: lower(c.videoCapabilities),
                audioCapabilities: lower(c.audioCapabilities),
            }));
        });
    }
    requestMediaKeySystemAccess.streamshell = true;
    proto.requestMediaKeySystemAccess = requestMediaKeySystemAccess;
})();
