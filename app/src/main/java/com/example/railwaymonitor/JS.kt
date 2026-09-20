package com.example.railwaymonitor

object JS {

    // ============================================================
    // BASIC CLICK
    // ============================================================

    fun clickTextButton(
        text: String
    ): String = """
        (function(){
            var wanted = '$text'.trim();

            var nodes =
                document.querySelectorAll(
                    'button, input[type=button], input[type=submit], a'
                );

            for(var i=0;i<nodes.length;i++){

                var el = nodes[i];

                var t =
                    (el.innerText ||
                     el.textContent ||
                     el.value ||
                     '').trim();

                if(t !== wanted) continue;

                var r =
                    el.getBoundingClientRect();

                if(
                    r.width > 0 &&
                    r.height > 0 &&
                    !el.disabled
                ){
                    el.click();
                    return true;
                }
            }

            return false;
        })();
    """.trimIndent()

    // ============================================================
    // CITY INPUT
    // ============================================================

    fun typeIntoCityField(
        controlName: String,
        city: String
    ): String = """
        (function(){

            var input =
                document.querySelector(
                    'input[formcontrolname="$controlName"]'
                );

            if(!input)
                return JSON.stringify({
                    ok:false,
                    reason:'input_not_found'
                });

            input.focus();
            input.click();

            var setter =
                Object.getOwnPropertyDescriptor(
                    HTMLInputElement.prototype,
                    'value'
                ).set;

            setter.call(input,'');

            input.dispatchEvent(
                new Event(
                    'input',
                    {bubbles:true}
                )
            );

            setter.call(input,'$city');

            input.dispatchEvent(
                new Event(
                    'input',
                    {bubbles:true}
                )
            );

            input.dispatchEvent(
                new Event(
                    'change',
                    {bubbles:true}
                )
            );

            input.dispatchEvent(
                new KeyboardEvent(
                    'keyup',
                    {
                        key:'$city',
                        bubbles:true
                    }
                )
            );

            return JSON.stringify({
                ok:true
            });

        })();
    """.trimIndent()

    fun clickCityOption(
        city: String
    ): String = """
        (function(){

            var wanted =
                '$city'.trim().toUpperCase();

            /*
             * প্রথমে autocomplete-type elements।
             */
            var selectors = [
                '[role="option"]',
                '.dropdown-item',
                '.autocomplete-item',
                'li'
            ];

            for(
                var s=0;
                s<selectors.length;
                s++
            ){

                var nodes =
                    document.querySelectorAll(
                        selectors[s]
                    );

                for(
                    var i=0;
                    i<nodes.length;
                    i++
                ){

                    var el = nodes[i];

                    var text =
                        (el.innerText ||
                         el.textContent ||
                         '').trim();

                    var rect =
                        el.getBoundingClientRect();

                    if(
                        text.toUpperCase() === wanted &&
                        rect.width > 0 &&
                        rect.height > 0
                    ){

                        el.scrollIntoView({
                            block:'center'
                        });

                        el.click();

                        return JSON.stringify({
                            ok:true,
                            method:'autocomplete',
                            text:text
                        });
                    }
                }
            }

            /*
             * Fallback: exact visible text.
             */
            var all =
                document.querySelectorAll(
                    'body *'
                );

            for(
                var j=0;
                j<all.length;
                j++
            ){

                var node = all[j];

                if(
                    node.children.length > 0
                ) continue;

                var t =
                    (node.textContent || '')
                        .trim();

                var r =
                    node.getBoundingClientRect();

                if(
                    t.toUpperCase() === wanted &&
                    r.width > 0 &&
                    r.height > 0
                ){

                    node.click();

                    return JSON.stringify({
                        ok:true,
                        method:'exact_text',
                        text:t
                    });
                }
            }

            return JSON.stringify({
                ok:false,
                reason:'city_option_not_found'
            });

        })();
    """.trimIndent()

    fun getInputValue(
        controlName: String
    ): String = """
        (function(){

            var input =
                document.querySelector(
                    'input[formcontrolname="$controlName"]'
                );

            return input
                ? (input.value || '')
                : '';

        })();
    """.trimIndent()

    // ============================================================
    // CLASS
    // ============================================================

    fun selectClass(
        cls: String
    ): String = """
        (function(){

            var wanted =
                '$cls'.trim().toUpperCase();

            /*
             * Native select.
             */
            var selects =
                document.querySelectorAll(
                    'select'
                );

            for(
                var s=0;
                s<selects.length;
                s++
            ){

                var select =
                    selects[s];

                var options =
                    select.querySelectorAll(
                        'option'
                    );

                for(
                    var o=0;
                    o<options.length;
                    o++
                ){

                    var opt =
                        options[o];

                    var value =
                        (opt.value || '')
                            .trim()
                            .toUpperCase();

                    var text =
                        (opt.textContent || '')
                            .trim()
                            .toUpperCase();

                    if(
                        value === wanted ||
                        text === wanted
                    ){

                        select.value =
                            opt.value;

                        opt.selected = true;

                        select.dispatchEvent(
                            new Event(
                                'input',
                                {bubbles:true}
                            )
                        );

                        select.dispatchEvent(
                            new Event(
                                'change',
                                {bubbles:true}
                            )
                        );

                        return JSON.stringify({
                            ok:true,
                            method:'select',
                            value:select.value,
                            text:opt.textContent.trim()
                        });
                    }
                }
            }

            /*
             * Custom Angular dropdown.
             */
            var nodes =
                document.querySelectorAll(
                    '[role="option"], button, li, div, span'
                );

            for(
                var i=0;
                i<nodes.length;
                i++
            ){

                var el = nodes[i];

                var text =
                    (el.innerText ||
                     el.textContent ||
                     '').trim();

                if(
                    text.toUpperCase() !== wanted
                ) continue;

                var r =
                    el.getBoundingClientRect();

                if(
                    r.width > 0 &&
                    r.height > 0
                ){

                    el.scrollIntoView({
                        block:'center'
                    });

                    el.click();

                    return JSON.stringify({
                        ok:true,
                        method:'custom',
                        text:text
                    });
                }
            }

            return JSON.stringify({
                ok:false,
                reason:'class_not_found'
            });

        })();
    """.trimIndent()

    fun verifyClass(
        cls: String
    ): String = """
        (function(){

            var wanted =
                '$cls'.trim().toUpperCase();

            /*
             * Native select check.
             */
            var selects =
                document.querySelectorAll(
                    'select'
                );

            for(
                var s=0;
                s<selects.length;
                s++
            ){

                var select =
                    selects[s];

                var value =
                    (select.value || '')
                        .trim()
                        .toUpperCase();

                var opt =
                    select.options[
                        select.selectedIndex
                    ];

                var text =
                    opt
                        ? (opt.textContent || '')
                            .trim()
                            .toUpperCase()
                        : '';

                if(
                    value === wanted ||
                    text === wanted
                ){

                    return JSON.stringify({
                        ok:true,
                        method:'select',
                        value:value,
                        text:text
                    });
                }
            }

            /*
             * Visible selected/custom class.
             */
            var nodes =
                document.querySelectorAll(
                    '[aria-selected="true"], .selected, .active'
                );

            for(
                var i=0;
                i<nodes.length;
                i++
            ){

                var t =
                    (nodes[i].innerText ||
                     nodes[i].textContent ||
                     '').trim()
                    .toUpperCase();

                if(
                    t === wanted ||
                    t.indexOf(wanted) >= 0
                ){

                    return JSON.stringify({
                        ok:true,
                        method:'custom',
                        text:t
                    });
                }
            }

            return JSON.stringify({
                ok:false
            });

        })();
    """.trimIndent()

    // ============================================================
    // SEARCH FORM STATE
    // ============================================================

    fun getSearchFormState(
        cls: String
    ): String = """
        (function(){

            var from =
                document.querySelector(
                    'input[formcontrolname="fromcity"]'
                );

            var to =
                document.querySelector(
                    'input[formcontrolname="tocity"]'
                );

            var date =
                document.querySelector(
                    'input[placeholder="Pick a date"]'
                ) ||
                document.querySelector(
                    'input.hasDatepicker'
                );

            var fromValue =
                from ? (from.value || '').trim() : '';

            var toValue =
                to ? (to.value || '').trim() : '';

            var dateValue =
                date ? (date.value || '').trim() : '';

            var classState =
                JSON.parse(
                    (${verifyClass(cls)})
                );

            var fromOk =
                fromValue.length > 0;

            var toOk =
                toValue.length > 0;

            var dateOk =
                dateValue.length > 0;

            var classOk =
                classState.ok === true;

            return JSON.stringify({
                ready:
                    fromOk &&
                    toOk &&
                    dateOk &&
                    classOk,

                from:fromValue,
                to:toValue,
                date:dateValue,
                classOk:classOk
            });

        })();
    """.trimIndent()

    // ============================================================
    // SEARCH BUTTON
    // ============================================================

    fun clickSearchIfReady(): String = """
        (function(){

            var buttons =
                document.querySelectorAll(
                    'button, input[type="submit"], input[type="button"]'
                );

            var candidates = [];

            for(
                var i=0;
                i<buttons.length;
                i++
            ){

                var b = buttons[i];

                var text =
                    (
                        b.innerText ||
                        b.textContent ||
                        b.value ||
                        ''
                    )
                    .replace(/\s+/g,' ')
                    .trim()
                    .toUpperCase();

                if(
                    text.indexOf('SEARCH') === -1
                ) continue;

                var r =
                    b.getBoundingClientRect();

                if(
                    r.width <= 0 ||
                    r.height <= 0
                ) continue;

                var disabled =
                    b.disabled === true ||
                    b.getAttribute(
                        'aria-disabled'
                    ) === 'true';

                if(disabled) continue;

                /*
                 * Search button-এর parent text দেখে
                 * form-এর button preference।
                 */
                var parentText =
                    b.parentElement
                        ? (
                            b.parentElement.innerText ||
                            ''
                        ).toUpperCase()
                        : '';

                var score = 0;

                if(
                    parentText.indexOf('FROM') >= 0
                ) score += 2;

                if(
                    parentText.indexOf('TO') >= 0
                ) score += 2;

                if(
                    parentText.indexOf('DATE') >= 0
                ) score += 2;

                candidates.push({
                    el:b,
                    score:score
                });
            }

            if(
                candidates.length === 0
            ){

                return JSON.stringify({
                    ok:false,
                    reason:'search_button_not_found'
                });
            }

            candidates.sort(
                function(a,b){
                    return b.score - a.score;
                }
            );

            var target =
                candidates[0].el;

            target.scrollIntoView({
                block:'center'
            });

            target.click();

            return JSON.stringify({
                ok:true,
                text:(
                    target.innerText ||
                    target.textContent ||
                    target.value ||
                    ''
                ).trim()
            });

        })();
    """.trimIndent()

    // ============================================================
    // NO TICKET
    // ============================================================

    fun checkNoTicketMarker(): String = """
        (function(){

            var text =
                (
                    document.body.innerText ||
                    ''
                )
                .replace(/\s+/g,' ')
                .toUpperCase();

            return text.indexOf(
                'NOT FINDING ANY TICKET FOR YOUR DESIRED ROUTE'
            ) >= 0;

        })();
    """.trimIndent()

    // ============================================================
    // AVAILABILITY
    // ============================================================

    fun checkAvailability(): String = """
        (function(){

            var buttons =
                Array.prototype.slice.call(
                    document.querySelectorAll(
                        'button'
                    )
                );

            var found = [];

            for(
                var i=0;
                i<buttons.length;
                i++
            ){

                var btn =
                    buttons[i];

                var text =
                    (
                        btn.innerText ||
                        btn.textContent ||
                        ''
                    )
                    .replace(/\s+/g,' ')
                    .trim();

                if(
                    text.toUpperCase()
                        .indexOf('BOOK NOW') === -1
                ) continue;

                var rect =
                    btn.getBoundingClientRect();

                if(
                    rect.width <= 0 ||
                    rect.height <= 0
                ) continue;

                var disabled =
                    btn.disabled === true ||
                    btn.getAttribute(
                        'aria-disabled'
                    ) === 'true' ||
                    (
                        String(
                            btn.className || ''
                        )
                        .toLowerCase()
                        .indexOf('disabled') >= 0
                    );

                if(disabled) continue;

                /*
                 * Try nearest seat-class card.
                 */
                var card =
                    btn.closest(
                        '.single-seat-class'
                    );

                if(!card){

                    card =
                        btn.closest(
                            '[class*="seat"]'
                        );
                }

                if(!card){
                    card = btn.parentElement;
                }

                var trip =
                    btn.closest(
                        '.single-trip-wrapper'
                    );

                if(!trip){

                    trip =
                        btn.closest(
                            '[class*="trip"]'
                        );
                }

                if(!trip){
                    trip = card;
                }

                var cardText =
                    card
                        ? (
                            card.innerText || ''
                        )
                        : '';

                /*
                 * Accept several possible formats:
                 *
                 * Available 5
                 * Available: 5
                 * Available - 5
                 */
                var match =
                    cardText.match(
                        /Available\s*[:\-]?\s*(\d+)/i
                    );

                var available =
                    match
                        ? parseInt(
                            match[1],
                            10
                        )
                        : 0;

                var className =
                    'UNKNOWN';

                var classMatch =
                    cardText.match(
                        /(S_CHAIR|F_CHAIR|SHOVAN|SHULOV|AC_CHAIR|AC_B|AC_S|SNIGDHA|F_BERTH|F_SEAT)/i
                    );

                if(classMatch){

                    className =
                        classMatch[1]
                            .toUpperCase();
                }

                var trainName =
                    'UNKNOWN TRAIN';

                if(trip){

                    var lines =
                        (
                            trip.innerText ||
                            ''
                        )
                        .split(/\n+/)
                        .map(
                            function(x){
                                return x.trim();
                            }
                        )
                        .filter(Boolean);

                    if(lines.length > 0){
                        trainName =
                            lines[0];
                    }
                }

                /*
                 * IMPORTANT:
                 * Enabled BOOK NOW alone is NOT treated
                 * as available.
                 */
                if(available > 0){

                    found.push({
                        train:trainName,
                        class_name:className,
                        available:available,
                        book_now_enabled:true
                    });
                }
            }

            return JSON.stringify(found);

        })();
    """.trimIndent()

    // ============================================================
    // DEBUG
    // ============================================================

    fun debugBookNowCount(): String = """
        (function(){

            var buttons =
                document.querySelectorAll(
                    'button'
                );

            var total = 0;
            var enabled = 0;

            for(
                var i=0;
                i<buttons.length;
                i++
            ){

                var btn =
                    buttons[i];

                var text =
                    (
                        btn.innerText ||
                        btn.textContent ||
                        ''
                    )
                    .trim()
                    .toUpperCase();

                if(
                    text.indexOf('BOOK NOW') === -1
                ) continue;

                total++;

                var rect =
                    btn.getBoundingClientRect();

                var disabled =
                    btn.disabled === true ||
                    btn.getAttribute(
                        'aria-disabled'
                    ) === 'true';

                if(
                    rect.width > 0 &&
                    rect.height > 0 &&
                    !disabled
                ){
                    enabled++;
                }
            }

            return JSON.stringify({
                total:total,
                enabled:enabled
            });

        })();
    """.trimIndent()

    // ============================================================
    // DATE PICKER
    // ============================================================

    fun openDatePicker(): String = """
        (function(){

            var input =
                document.querySelector(
                    'input.hasDatepicker[placeholder="Pick a date"]'
                ) ||
                document.querySelector(
                    'input[placeholder="Pick a date"]'
                ) ||
                document.querySelector(
                    'input.hasDatepicker'
                );

            if(!input){

                return JSON.stringify({
                    ok:false,
                    reason:'input_not_found'
                });
            }

            input.focus();
            input.click();

            return JSON.stringify({
                ok:true
            });

        })();
    """.trimIndent()

    fun readCalendarHeader(): String = """
        (function(){

            function read(el){

                if(!el)
                    return null;

                if(
                    el.tagName === 'SELECT'
                ){

                    var opt =
                        el.options[
                            el.selectedIndex
                        ];

                    return opt
                        ? opt.textContent.trim()
                        : el.value;
                }

                return (
                    el.textContent || ''
                ).trim();
            }

            var month =
                document.querySelector(
                    '.ui-datepicker-month'
                );

            var year =
                document.querySelector(
                    '.ui-datepicker-year'
                );

            /*
             * Fallback for other calendar
             * implementations.
             */
            if(!month){

                month =
                    document.querySelector(
                        '.datepicker .month'
                    );
            }

            if(!year){

                year =
                    document.querySelector(
                        '.datepicker .year'
                    );
            }

            return JSON.stringify({
                month:read(month),
                year:read(year)
            });

        })();
    """.trimIndent()

    fun clickCalendarArrow(
        direction: String
    ): String {

        val css =
            if(direction == "next")
                ".ui-datepicker-next"
            else
                ".ui-datepicker-prev"

        return """
            (function(){

                var el =
                    document.querySelector(
                        '$css'
                    );

                if(!el){

                    return JSON.stringify({
                        ok:false,
                        reason:'arrow_not_found'
                    });
                }

                var disabled =
                    (
                        String(
                            el.className || ''
                        )
                        .indexOf(
                            'ui-state-disabled'
                        ) >= 0
                    );

                if(disabled){

                    return JSON.stringify({
                        ok:false,
                        reason:'disabled'
                    });
                }

                el.click();

                return JSON.stringify({
                    ok:true
                });

            })();
        """.trimIndent()
    }

    fun clickCalendarDay(
        day: Int
    ): String = """
        (function(){

            var wanted =
                '$day';

            var links =
                document.querySelectorAll(
                    '.ui-datepicker-calendar td a'
                );

            for(
                var i=0;
                i<links.length;
                i++
            ){

                var a =
                    links[i];

                if(
                    (a.textContent || '')
                        .trim() !== wanted
                ) continue;

                var td =
                    a.closest('td');

                if(
                    td &&
                    String(
                        td.className || ''
                    ).indexOf(
                        'ui-datepicker-other-month'
                    ) >= 0
                ){
                    continue;
                }

                a.click();

                return JSON.stringify({
                    ok:true
                });
            }

            return JSON.stringify({
                ok:false,
                reason:'day_not_found'
            });

        })();
    """.trimIndent()

    fun getDateInputValue(): String = """
        (function(){

            var input =
                document.querySelector(
                    'input.hasDatepicker[placeholder="Pick a date"]'
                ) ||
                document.querySelector(
                    'input[placeholder="Pick a date"]'
                ) ||
                document.querySelector(
                    'input.hasDatepicker'
                );

            return input
                ? (input.value || '')
                : '';

        })();
    """.trimIndent()

    fun verifyDateValue(
        day: Int,
        monthIndex: Int,
        year: Int
    ): String = """
        (function(){

            var input =
                document.querySelector(
                    'input.hasDatepicker[placeholder="Pick a date"]'
                ) ||
                document.querySelector(
                    'input[placeholder="Pick a date"]'
                ) ||
                document.querySelector(
                    'input.hasDatepicker'
                );

            if(!input){

                return JSON.stringify({
                    ok:false
                });
            }

            var value =
                (input.value || '').trim();

            var dayText =
                String($day);

            var monthText =
                String(${
                    monthIndex + 1
                });

            var yearText =
                String($year);

            /*
             * Date input format may be:
             * DD-MM-YYYY
             * DD/MM/YYYY
             * YYYY-MM-DD
             *
             * Check all reasonable forms.
             */
            var normalized =
                value.replace(/\//g,'-');

            var ok =
                normalized ===
                    dayText.padStart(2,'0') +
                    '-' +
                    monthText.padStart(2,'0') +
                    '-' +
                    yearText
                ||
                normalized ===
                    yearText +
                    '-' +
                    monthText.padStart(2,'0') +
                    '-' +
                    dayText.padStart(2,'0')
                ||
                normalized.indexOf(
                    dayText.padStart(2,'0')
                ) >= 0 &&
                normalized.indexOf(
                    yearText
                ) >= 0;

            return JSON.stringify({
                ok:ok,
                value:value
            });

        })();
    """.trimIndent()

    // ============================================================
    // QUICK SEARCH — ROUTE SPECIFIC
    // ============================================================

    fun clickSuggestedSearchForRoute(
        fromCity: String,
        toCity: String
    ): String = """
        (function(){

            var fromWanted =
                '$fromCity'
                    .trim()
                    .toUpperCase();

            var toWanted =
                '$toCity'
                    .trim()
                    .toUpperCase();

            var buttons =
                Array.prototype.slice.call(
                    document.querySelectorAll(
                        'button'
                    )
                );

            var candidates = [];

            for(
                var i=0;
                i<buttons.length;
                i++
            ){

                var btn =
                    buttons[i];

                var buttonText =
                    (
                        btn.innerText ||
                        btn.textContent ||
                        ''
                    )
                    .replace(/\s+/g,' ')
                    .trim();

                if(
                    buttonText.toUpperCase() !==
                    'SEARCH'
                ) continue;

                var rect =
                    btn.getBoundingClientRect();

                if(
                    rect.width <= 0 ||
                    rect.height <= 0 ||
                    btn.disabled
                ) continue;

                /*
                 * Walk up a few levels and inspect
                 * surrounding suggested-route text.
                 */
                var parent = btn;
                var context = '';

                for(
                    var level=0;
                    level<6 && parent;
                    level++
                ){

                    context += ' ' +
                        (
                            parent.innerText ||
                            ''
                        );

                    parent =
                        parent.parentElement;
                }

                context =
                    context
                        .replace(/\s+/g,' ')
                        .toUpperCase();

                var fromFound =
                    context.indexOf(
                        fromWanted
                    ) >= 0;

                var toFound =
                    context.indexOf(
                        toWanted
                    ) >= 0;

                var score = 0;

                if(fromFound)
                    score += 10;

                if(toFound)
                    score += 10;

                /*
                 * Suggested route normally contains
                 * at least FROM or TO.
                 */
                if(
                    fromFound ||
                    toFound
                ){
                    score += 5;
                }

                candidates.push({
                    el:btn,
                    score:score,
                    context:context
                });
            }

            if(
                candidates.length === 0
            ){

                return JSON.stringify({
                    ok:false,
                    reason:'no_suggested_search',
                    found:0
                });
            }

            candidates.sort(
                function(a,b){
                    return b.score - a.score;
                }
            );

            var best =
                candidates[0];

            /*
             * We require at least one expected
             * route endpoint before quick click.
             */
            if(best.score < 5){

                return JSON.stringify({
                    ok:false,
                    reason:'route_not_matched',
                    found:candidates.length
                });
            }

            best.el.scrollIntoView({
                block:'center'
            });

            best.el.click();

            return JSON.stringify({
                ok:true,
                score:best.score,
                candidates:candidates.length,
                context:best.context.substring(
                    0,
                    500
                )
            });

        })();
    """.trimIndent()

    // ============================================================
    // RESULT ROUTE VERIFICATION
    // ============================================================

    fun verifyResultRoute(
        fromCity: String,
        toCity: String
    ): String = """
        (function(){

            var body =
                (
                    document.body.innerText ||
                    ''
                )
                .replace(/\s+/g,' ')
                .toUpperCase();

            var from =
                '$fromCity'
                    .trim()
                    .toUpperCase();

            var to =
                '$toCity'
                    .trim()
                    .toUpperCase();

            var fromFound =
                body.indexOf(from) >= 0;

            var toFound =
                body.indexOf(to) >= 0;

            return JSON.stringify({
                ok:
                    fromFound &&
                    toFound,

                fromFound:fromFound,
                toFound:toFound
            });

        })();
    """.trimIndent()

    // ============================================================
    // BODY
    // ============================================================

    fun getBodyText(): String =
        "document.body.innerText || '';"

    // ============================================================
    // DATE DIAGNOSTIC
    // ============================================================

    fun dumpDateFieldHtml(): String = """
        (function(){

            var extra = {

                readyState:
                    document.readyState,

                iframeCount:
                    document.querySelectorAll(
                        'iframe'
                    ).length,

                idDoiCount:
                    document.querySelectorAll(
                        'input#doi'
                    ).length,

                hasDatepickerCount:
                    document.querySelectorAll(
                        'input.hasDatepicker'
                    ).length,

                placeholderCount:
                    document.querySelectorAll(
                        'input[placeholder="Pick a date"]'
                    ).length,

                allInputCount:
                    document.querySelectorAll(
                        'input'
                    ).length
            };

            var inputs =
                document.querySelectorAll(
                    'input'
                );

            var dateHtml = '';

            for(
                var i=0;
                i<inputs.length;
                i++
            ){

                var input =
                    inputs[i];

                var ph =
                    input.getAttribute(
                        'placeholder'
                    ) || '';

                var cls =
                    input.className || '';

                if(
                    ph.indexOf(
                        'date'
                    ) >= 0 ||
                    String(cls).indexOf(
                        'datepicker'
                    ) >= 0
                ){

                    dateHtml =
                        input.outerHTML;

                    break;
                }
            }

            if(
                dateHtml.length > 1200
            ){
                dateHtml =
                    dateHtml.substring(
                        0,
                        1200
                    ) +
                    '...(truncated)';
            }

            return JSON.stringify({
                extra:extra,
                html:dateHtml
            });

        })();
    """.trimIndent()
}