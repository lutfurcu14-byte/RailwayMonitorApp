package com.example.railwaymonitor

/**
 * প্রতিটি ফাংশন একটি JS এক্সপ্রেশন/স্টেটমেন্ট রিটার্ন করে, যা
 * WebView.evaluateJavascript(...) দিয়ে চালানো হবে।
 * এই লজিকগুলো railway_MULTI_ROUTE_SMART_12ROUTE_5SEC_v4.py থেকে
 * সরাসরি অনুপ্রাণিত/পোর্ট করা।
 */
object JS {

    fun clickTextButton(text: String): String = """
        (function(){
            var xp = "//*[normalize-space(text())='$text']";
            var r = document.evaluate(xp, document, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
            for (var i=0;i<r.snapshotLength;i++){
                var el = r.snapshotItem(i);
                var rect = el.getBoundingClientRect();
                if (rect.width>0 && rect.height>0){ el.click(); return true; }
            }
            return false;
        })();
    """.trimIndent()

    fun typeIntoCityField(controlName: String, city: String): String = """
        (function(){
            var input = document.querySelector('input[formcontrolname="$controlName"]');
            if (!input) return false;
            input.focus();
            input.click();
            var setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
            setter.call(input, '');
            input.dispatchEvent(new Event('input', {bubbles:true}));
            setter.call(input, '$city');
            input.dispatchEvent(new Event('input', {bubbles:true}));
            input.dispatchEvent(new Event('keyup', {bubbles:true}));
            return true;
        })();
    """.trimIndent()

    fun clickCityOption(city: String): String = """
        (function(){
            var xp = "//*[normalize-space(text())='$city']";
            var r = document.evaluate(xp, document, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
            for (var i=0;i<r.snapshotLength;i++){
                var el = r.snapshotItem(i);
                var rect = el.getBoundingClientRect();
                if (rect.width>0 && rect.height>0){ el.click(); return true; }
            }
            return false;
        })();
    """.trimIndent()

    fun getInputValue(controlName: String): String = """
        (function(){
            var input = document.querySelector('input[formcontrolname="$controlName"]');
            return input ? input.value : '';
        })();
    """.trimIndent()

    fun selectClass(cls: String): String = """
        (function(){
            var selects = document.querySelectorAll('select');
            for (var s=0; s<selects.length; s++){
                var select = selects[s];
                var options = select.querySelectorAll('option');
                for (var o=0;o<options.length;o++){
                    var opt = options[o];
                    if ((opt.value||'').trim()==='$cls' || (opt.textContent||'').trim()==='$cls'){
                        select.value = opt.value;
                        select.dispatchEvent(new Event('change', {bubbles:true}));
                        return true;
                    }
                }
            }
            var xp = "//*[contains(normalize-space(.),'$cls')]";
            var r = document.evaluate(xp, document, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
            for (var i=0;i<r.snapshotLength;i++){
                var el = r.snapshotItem(i);
                var rect = el.getBoundingClientRect();
                if (rect.width>0 && rect.height>0){ el.click(); return true; }
            }
            return false;
        })();
    """.trimIndent()

    fun clickSearchIfReady(): String = """
        (function(){
            var candidates = [];
            document.querySelectorAll('button[type=submit], input[type=submit]').forEach(function(b){candidates.push(b);});
            document.querySelectorAll('button').forEach(function(b){
                if ((b.textContent||'').toUpperCase().indexOf('SEARCH')>=0) candidates.push(b);
            });
            for (var i=0;i<candidates.length;i++){
                var b = candidates[i];
                var rect = b.getBoundingClientRect();
                if (rect.width>0 && rect.height>0 && !b.disabled && b.getAttribute('aria-disabled')!=='true'){
                    b.click();
                    return JSON.stringify({ok:true});
                }
            }
            return JSON.stringify({ok:false});
        })();
    """.trimIndent()

    fun checkNoTicketMarker(): String = """
        (function(){
            var text = (document.body.innerText||'').replace(/\s+/g,' ').toUpperCase();
            return text.indexOf('NOT FINDING ANY TICKET FOR YOUR DESIRED ROUTE') >= 0;
        })();
    """.trimIndent()

    // পাইথন স্ক্রিপ্টের check_ticket_availability() JS ব্লকের সরাসরি পোর্ট।
    fun checkAvailability(): String = """
        (function(){
            var output = [];
            var labels = Array.prototype.slice.call(document.querySelectorAll('.available-text'));
            labels.forEach(function(label){
                var card = label.closest('.single-seat-class');
                var trip = label.closest('.single-trip-wrapper');
                if (!card || !trip) return;

                var tripLines = trip.innerText.split(/\n+/).map(function(x){return x.trim();}).filter(Boolean);
                var trainName = tripLines.length ? tripLines[0] : 'UNKNOWN TRAIN';

                var cardLines = card.innerText.split(/\n+/).map(function(x){return x.trim();}).filter(Boolean);
                if (!cardLines.length) return;
                var className = cardLines[0];

                var labelLines = label.innerText.split(/\n+/).map(function(x){return x.trim();}).filter(Boolean);
                var available = 0;
                if (labelLines.length > 0) {
                    var n = parseInt(labelLines[labelLines.length - 1], 10);
                    if (!isNaN(n)) available = n;
                }

                var buttons = Array.prototype.slice.call(card.querySelectorAll('button'));
                var bookNowEnabled = buttons.some(function(button){
                    return button.innerText.trim().toUpperCase().indexOf('BOOK NOW') >= 0
                        && !button.disabled
                        && button.offsetParent !== null;
                });

                var availableByClass = card.classList.contains('seat-available-wrap');

                output.push({
                    train: trainName,
                    class_name: className,
                    available: available,
                    book_now_enabled: bookNowEnabled,
                    available_by_class: availableByClass
                });
            });
            return JSON.stringify(output);
        })();
    """.trimIndent()

    // পাইথন স্ক্রিপ্টের force_search_date_final() / synchronize_railway_search_date()
    // ফাংশনের প্রায় হুবহু পোর্ট — jQuery datepicker + hidden Angular formcontrol
    // + Angular FormControl (component-tree walk) — সব একসাথে ফোর্স-সিঙ্ক করে।
    fun forceSyncDate(isoDate: String): String = """
        (function(){
            var target = '$isoDate';
            var visible = document.querySelector('#doj') || document.querySelector('input.datepicker.hasDatepicker');
            var hidden = document.querySelector('input[type="hidden"][formcontrolname="doj"]');
            var months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
            var parts = target.split('-');
            var y = parseInt(parts[0],10), m = parseInt(parts[1],10)-1, d = parseInt(parts[2],10);

            try {
                if (window.jQuery && visible && jQuery.fn.datepicker) {
                    var dt = new Date(y, m, d);
                    jQuery(visible).datepicker('setDate', dt);
                    jQuery(visible).val(String(d).padStart(2,'0') + '-' + months[m] + '-' + y);
                    jQuery(visible).trigger('change');
                }
            } catch (e) {}

            if (visible) {
                try {
                    var setterV = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
                    setterV.call(visible, String(d).padStart(2,'0') + '-' + months[m] + '-' + y);
                    visible.dispatchEvent(new Event('input', {bubbles:true}));
                    visible.dispatchEvent(new Event('change', {bubbles:true}));
                } catch (e) {}
            }

            if (hidden) {
                try {
                    var setterH = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
                    setterH.call(hidden, target);
                    hidden.dispatchEvent(new Event('input', {bubbles:true}));
                    hidden.dispatchEvent(new Event('change', {bubbles:true}));
                } catch (e) {}
            }

            var controlValue = null;
            try {
                var roots = [];
                [visible, hidden].forEach(function(input){
                    var node = input;
                    for (var i=0; node && i<15; i++, node=node.parentElement) {
                        if (node.__ngContext__) roots.push(node.__ngContext__);
                    }
                });
                var seen = new WeakSet();
                var queue = roots.map(function(r){ return {obj:r, depth:0}; });
                while (queue.length) {
                    var item = queue.shift();
                    var obj = item.obj;
                    if (!obj || (typeof obj !== 'object' && typeof obj !== 'function')) continue;
                    if (seen.has(obj)) continue;
                    seen.add(obj);
                    try {
                        if (obj.controls && obj.controls.doj && typeof obj.controls.doj.setValue === 'function') {
                            obj.controls.doj.setValue(target);
                            obj.controls.doj.updateValueAndValidity();
                            controlValue = obj.controls.doj.value;
                            break;
                        }
                    } catch (e) {}
                    if (item.depth >= 7) continue;
                    var keys = [];
                    try { keys = Object.keys(obj).slice(0,120); } catch(e) {}
                    for (var k=0; k<keys.length; k++) {
                        var key = keys[k];
                        if (key==='nativeElement'||key==='renderer'||key==='elementRef'||key==='ownerDocument'||key==='parentNode') continue;
                        var v;
                        try { v = obj[key]; } catch(e) { continue; }
                        if (v && (typeof v === 'object' || typeof v === 'function')) {
                            queue.push({obj:v, depth:item.depth+1});
                        }
                    }
                }
            } catch(e) {}

            return JSON.stringify({hidden: hidden ? hidden.value : null, controlValue: controlValue});
        })();
    """.trimIndent()
}
