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

    // পুরনো ভার্সনে .available-text / .single-seat-class ইত্যাদি নির্দিষ্ট
    // CSS ক্লাস নামের উপর নির্ভর করা হতো, যেগুলো যাচাই করা যায়নি (সাইট
    // ব্লক করা)। এখন এর বদলে সরাসরি স্ক্রিনশটে-দেখা সবুজ "BOOK NOW" বাটন
    // (enabled অবস্থায়) খোঁজা হচ্ছে — এটা অনেক বেশি নির্ভরযোগ্য, কারণ এটা
    // চোখে দেখে নিশ্চিত করা প্যাটার্ন।
    fun checkAvailability(): String = """
        (function(){
            var buttons = Array.prototype.slice.call(document.querySelectorAll('button'));
            var found = [];
            for (var i=0;i<buttons.length;i++){
                var btn = buttons[i];
                var text = (btn.textContent||'').trim().toUpperCase();
                if (text.indexOf('BOOK NOW') === -1) continue;
                var rect = btn.getBoundingClientRect();
                if (rect.width<=0 || rect.height<=0) continue;
                var disabled = btn.disabled === true || btn.getAttribute('aria-disabled')==='true'
                    || (btn.className||'').toString().toLowerCase().indexOf('disabled')>=0;
                if (disabled) continue;

                var card = btn.closest('.single-seat-class') || btn.parentElement || btn;
                var trip = btn.closest('.single-trip-wrapper') || card;

                var className = 'UNKNOWN';
                var available = 0;
                if (card) {
                    var cardLines = (card.innerText||'').split(/\\n+/).map(function(x){return x.trim();}).filter(Boolean);
                    if (cardLines.length) className = cardLines[0];
                    var availMatch = (card.innerText||'').match(/Available[^0-9]*(\\d+)/i);
                    if (availMatch) available = parseInt(availMatch[1],10) || 0;
                }

                var trainName = 'UNKNOWN TRAIN';
                if (trip) {
                    var tripLines = (trip.innerText||'').split(/\\n+/).map(function(x){return x.trim();}).filter(Boolean);
                    if (tripLines.length) trainName = tripLines[0];
                }

                found.push({
                    train: trainName,
                    class_name: className,
                    available: available,
                    book_now_enabled: true,
                    available_by_class: true
                });
            }
            return JSON.stringify(found);
        })();
    """.trimIndent()

    // ডিবাগ: পেজে মোট কতটা "BOOK NOW" বাটন আছে আর কতটা সক্রিয় — লগে দেখানোর
    // জন্য, যাতে ভবিষ্যতে কোনো মিস হলে বোঝা যায় বাটনই খুঁজে পায়নি, নাকি
    // খুঁজে পেয়েও ভুল বিচার করেছে।
    fun debugBookNowCount(): String = """
        (function(){
            var buttons = Array.prototype.slice.call(document.querySelectorAll('button'));
            var total = 0, enabled = 0;
            for (var i=0;i<buttons.length;i++){
                var btn = buttons[i];
                var text = (btn.textContent||'').trim().toUpperCase();
                if (text.indexOf('BOOK NOW') === -1) continue;
                total++;
                var rect = btn.getBoundingClientRect();
                var disabled = btn.disabled === true || btn.getAttribute('aria-disabled')==='true';
                if (rect.width>0 && rect.height>0 && !disabled) enabled++;
            }
            return JSON.stringify({total:total, enabled:enabled});
        })();
    """.trimIndent()

    // পুরনো jQuery-datepicker ধরে নেওয়া লজিকটা বাদ (সাইটে আসলে একটা পপ-আপ
    // ক্যালেন্ডার-পিকার আছে, ngx-bootstrap bs-datepicker ধাঁচের — সবুজ হেডার,
    // ‹ › অ্যারো, টেবিল গ্রিড)। তাই এখন সরাসরি UI ক্লিক করে তারিখ বসানো হয়।

    fun openDatePicker(): String = """
        (function(){
            // প্রথমে চেষ্টা: সত্যিকারের <input placeholder="Pick a date">
            var input = document.querySelector('input[placeholder="Pick a date"]')
                || document.querySelector('input[placeholder*="date" i]');
            if (input) {
                input.click();
                input.dispatchEvent(new Event('focus', {bubbles:true}));
                return JSON.stringify({ok:true, via:'input'});
            }

            // ফলব্যাক: এটা হয়তো <input> না, বরং কোনো button/div/span যাতে
            // "Pick a date" লেখা টেক্সট আছে (অনেক ডেটপিকার লাইব্রেরি এভাবেই
            // ট্রিগার এলিমেন্ট বানায়)
            var xp = "//*[contains(normalize-space(text()),'Pick a date')]";
            var r = document.evaluate(xp, document, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
            for (var i=0;i<r.snapshotLength;i++){
                var el = r.snapshotItem(i);
                var rect = el.getBoundingClientRect();
                if (rect.width>0 && rect.height>0){
                    el.click();
                    return JSON.stringify({ok:true, via:'text-element'});
                }
            }

            // আরেকটা ফলব্যাক: "Date of Journey" লেবেলের ঠিক পরের ইনপুট/বক্স
            var labelXp = "//*[contains(normalize-space(text()),'Date of Journey')]";
            var lr = document.evaluate(labelXp, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null);
            var label = lr.singleNodeValue;
            if (label) {
                var container = label.parentElement;
                if (container) {
                    var clickable = container.querySelector('input, button, div[role], [tabindex]');
                    if (clickable) {
                        var rect2 = clickable.getBoundingClientRect();
                        if (rect2.width>0 && rect2.height>0) {
                            clickable.click();
                            return JSON.stringify({ok:true, via:'near-label'});
                        }
                    }
                }
            }

            return JSON.stringify({ok:false, reason:'input_not_found'});
        })();
    """.trimIndent()

    // ক্যালেন্ডার হেডারে বর্তমানে কোন মাস/বছর দেখাচ্ছে সেটা পড়া, যেমন "September 2026"
    fun readCalendarHeader(): String = """
        (function(){
            var months = ['January','February','March','April','May','June','July','August','September','October','November','December'];
            var pattern = new RegExp('(' + months.join('|') + ')\\s+(\\d{4})');
            var all = document.querySelectorAll('button, span, div, td, th');
            for (var i=0;i<all.length;i++){
                var t = (all[i].textContent||'').trim();
                var m = t.match(pattern);
                if (m && t.length < 30) {
                    var rect = all[i].getBoundingClientRect();
                    if (rect.width>0 && rect.height>0) return JSON.stringify({month:m[1], year:m[2]});
                }
            }
            return JSON.stringify({month:null, year:null});
        })();
    """.trimIndent()

    // হেডারের পাশের ‹ (previous) বা › (next) অ্যারোতে ক্লিক করা
    fun clickCalendarArrow(direction: String): String {
        val symbol = if (direction == "next") "›" else "‹"
        val altSymbol = if (direction == "next") ">" else "<"
        return """
        (function(){
            var candidates = Array.prototype.slice.call(document.querySelectorAll('button, span, a, i'));
            for (var i=0;i<candidates.length;i++){
                var el = candidates[i];
                var t = (el.textContent||'').trim();
                var cls = (el.className||'').toString().toLowerCase();
                var aria = (el.getAttribute('aria-label')||'').toLowerCase();
                var isNext = cls.indexOf('next')>=0 || aria.indexOf('next')>=0 || t==='$symbol' || t==='$altSymbol';
                var isPrev = cls.indexOf('previous')>=0 || cls.indexOf('prev')>=0 || aria.indexOf('previous')>=0 || aria.indexOf('prev')>=0;
                var want = '$direction';
                if ((want==='next' && isNext) || (want==='previous' && (cls.indexOf('prev')>=0 || aria.indexOf('prev')>=0 || t==='‹' || t==='<'))) {
                    var rect = el.getBoundingClientRect();
                    if (rect.width>0 && rect.height>0) { el.click(); return JSON.stringify({ok:true}); }
                }
            }
            return JSON.stringify({ok:false});
        })();
        """.trimIndent()
    }

    // ক্যালেন্ডারে সঠিক দিন-সংখ্যায় ক্লিক করা (disabled/গ্রে করা দিন বাদ দিয়ে)
    fun clickCalendarDay(day: Int): String = """
        (function(){
            var target = '$day';
            var cells = Array.prototype.slice.call(document.querySelectorAll('td, span, div, button'));
            var matches = [];
            for (var i=0;i<cells.length;i++){
                var el = cells[i];
                var t = (el.textContent||'').trim();
                if (t !== target) continue;
                var rect = el.getBoundingClientRect();
                if (rect.width<=0 || rect.height<=0) continue;
                var disabled = el.disabled === true || el.getAttribute('aria-disabled')==='true';
                var cls = (el.className||'').toString().toLowerCase();
                if (disabled || cls.indexOf('disabled')>=0) continue;
                matches.push(el);
            }
            if (matches.length === 0) return JSON.stringify({ok:false});
            // সবচেয়ে "গভীর" (deepest / clickable leaf) এলিমেন্টটা ক্লিক করা
            var el = matches[matches.length-1];
            el.click();
            return JSON.stringify({ok:true});
        })();
    """.trimIndent()

    // পাইথন স্ক্রিপ্টের click_suggested_search()-এর পোর্ট — "no ticket" পেজের
    // "Try Searching with other routes" বক্সে দৃশ্যমান Nth "Search" বাটনে ক্লিক
    // করে সরাসরি পরের রুটে যাওয়া (হোমপেজে ফিরে আবার ফর্ম না ভরে)।
    fun clickSuggestedSearch(buttonIndex: Int): String = """
        (function(){
            var buttons = Array.prototype.slice.call(document.querySelectorAll('button'));
            var visible = buttons.filter(function(b){
                var t = (b.textContent||'').trim();
                if (t !== 'Search') return false;
                var rect = b.getBoundingClientRect();
                return rect.width>0 && rect.height>0 && !b.disabled;
            });
            var idx = $buttonIndex - 1;
            if (idx < 0 || idx >= visible.length) return JSON.stringify({ok:false, found:visible.length});
            visible[idx].scrollIntoView({block:'center'});
            visible[idx].click();
            return JSON.stringify({ok:true});
        })();
    """.trimIndent()

    fun getBodyText(): String = "document.body.innerText || '';"

    // ডায়াগনস্টিক: "Date of Journey" এর আশেপাশের আসল HTML বের করা, যাতে
    // অনুমান না করে সত্যিকারের DOM গঠন দেখে সঠিক সিলেক্টর লেখা যায়।
    fun dumpDateFieldHtml(): String = """
        (function(){
            var labelXp = "//*[contains(normalize-space(text()),'Date of Journey')]";
            var lr = document.evaluate(labelXp, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null);
            var label = lr.singleNodeValue;
            if (!label) return JSON.stringify({found:false});
            var node = label;
            for (var i=0;i<3 && node.parentElement;i++){ node = node.parentElement; }
            var html = node.outerHTML || '';
            if (html.length > 1200) html = html.substring(0, 1200) + '...(truncated)';
            return JSON.stringify({found:true, html:html});
        })();
    """.trimIndent()

    fun getDateInputValue(): String = """
        (function(){
            var input = document.querySelector('input[placeholder="Pick a date"]')
                || document.querySelector('input[placeholder*="date" i]');
            if (input) return input.value || '';

            var labelXp = "//*[contains(normalize-space(text()),'Date of Journey')]";
            var lr = document.evaluate(labelXp, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null);
            var label = lr.singleNodeValue;
            if (label && label.parentElement) {
                var t = (label.parentElement.innerText||'').replace('Date of Journey','').trim();
                return t;
            }
            return '';
        })();
    """.trimIndent()
}
