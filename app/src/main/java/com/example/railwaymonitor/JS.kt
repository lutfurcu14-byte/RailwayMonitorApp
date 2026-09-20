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
            // ডায়াগনস্টিক কাউন্ট থেকে নিশ্চিত: class ও placeholder একসাথে
            // মিলিয়ে খুঁজলে নির্ভুলভাবে আসল একমাত্র ইনপুটটা পাওয়া যায়
            // (id="doi" দিয়ে কেন যেন মেলে না, সম্ভবত রেসপন্সিভ ডুপ্লিকেট
            // মার্কআপের কারণে)।
            var input = document.querySelector('input.hasDatepicker[placeholder="Pick a date"]')
                || document.querySelector('input[placeholder="Pick a date"]')
                || document.querySelector('input.hasDatepicker');
            if (!input) return JSON.stringify({ok:false, reason:'input_not_found'});
            input.focus();
            input.click();
            return JSON.stringify({ok:true});
        })();
    """.trimIndent()

    // jQuery UI Datepicker-এর real ক্লাস নাম (.ui-datepicker-month/-year) দিয়ে
    // হেডার পড়া — মাস/বছর plain span অথবা <select> (changeMonth/changeYear
    // চালু থাকলে) দুই অবস্থাতেই কাজ করবে।
    fun readCalendarHeader(): String = """
        (function(){
            function readEl(el){
                if (!el) return null;
                if (el.tagName === 'SELECT') {
                    var opt = el.options[el.selectedIndex];
                    return opt ? opt.textContent.trim() : el.value;
                }
                return (el.textContent||'').trim();
            }
            var monthEl = document.querySelector('.ui-datepicker-month');
            var yearEl = document.querySelector('.ui-datepicker-year');
            return JSON.stringify({month: readEl(monthEl), year: readEl(yearEl)});
        })();
    """.trimIndent()

    fun clickCalendarArrow(direction: String): String {
        val cssClass = if (direction == "next") "ui-datepicker-next" else "ui-datepicker-prev"
        return """
        (function(){
            var el = document.querySelector('.$cssClass');
            if (!el) return JSON.stringify({ok:false});
            if ((el.className||'').indexOf('ui-state-disabled')>=0) return JSON.stringify({ok:false, reason:'disabled'});
            el.click();
            return JSON.stringify({ok:true});
        })();
        """.trimIndent()
    }

    fun clickCalendarDay(day: Int): String = """
        (function(){
            var target = '$day';
            var links = document.querySelectorAll('.ui-datepicker-calendar td a');
            for (var i=0;i<links.length;i++){
                var a = links[i];
                if ((a.textContent||'').trim() === target) {
                    var td = a.closest('td');
                    if (td && (td.className||'').indexOf('ui-datepicker-other-month')>=0) continue;
                    a.click();
                    return JSON.stringify({ok:true});
                }
            }
            return JSON.stringify({ok:false});
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
            var extra = {
                readyState: document.readyState,
                iframeCount: document.querySelectorAll('iframe').length,
                idDoiCount: document.querySelectorAll('input#doi').length,
                hasDatepickerCount: document.querySelectorAll('input.hasDatepicker').length,
                placeholderCount: document.querySelectorAll('input[placeholder="Pick a date"]').length,
                allInputCount: document.querySelectorAll('input').length
            };
            if (!label) return JSON.stringify({found:false, extra:extra});
            var node = label;
            for (var i=0;i<3 && node.parentElement;i++){ node = node.parentElement; }
            var html = node.outerHTML || '';
            if (html.length > 900) html = html.substring(0, 900) + '...(truncated)';
            return JSON.stringify({found:true, html:html, extra:extra});
        })();
    """.trimIndent()

    fun getDateInputValue(): String = """
        (function(){
            var input = document.querySelector('input.hasDatepicker[placeholder="Pick a date"]')
                || document.querySelector('input[placeholder="Pick a date"]')
                || document.querySelector('input.hasDatepicker');
            return input ? (input.value || '') : '';
        })();
    """.trimIndent()
}
