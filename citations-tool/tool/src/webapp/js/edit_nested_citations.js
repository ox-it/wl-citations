(function () {

    $(document).ready(function(){

        var isEditingEnabled,
            toggle = $("input[id^='toggle']"),
            removeButton = $("input[id^='removeSection']"),
            addH1SubsectionButton = $("input[id^='addH1SubsectionButton']"),
            addH2SubsectionButton = $("input[id^='addH2SubsectionButton']"),
            SECTION_INLINE_EDITOR = 'sectionInlineEditor',
            TOGGLE = 'toggle';

        CKEDITOR.disableAutoInline = true; // don't show ckeditor on page load

        // handler for adding a section
        $('#addSectionButton').on('click', function() {

            // determine location on page
            var locationId = 1;
            $('#addSectionDiv').prevAll('li[id^="link"]').each(function() {
                locationId = locationId + 1 + $(this).find('li[id^="link"]').size();
            });

            // create html
            var divId = 'sectionInlineEditor' + locationId;
            var toggleId = 'toggle' + locationId;
            var removeDivId = 'removeSection' + locationId;
            var addSubsectionButtonId = 'addH1SubsectionButton' + locationId;
            var sectionTitle = $('#sectionTitleText').val();
            var startEditingText = $('#startEditingText').val();
            var deleteSectionText = $('#deleteSectionText').val();
            var addSubsectionButtonText = $('#addSubsectionButtonText').val();
            var html = "<li id='link" + locationId + "' class='months h1Editor sectionEditor' data-value='<h1>" + sectionTitle + "</h1>' data-location='" + locationId + "' data-sectiontype='HEADING1'><div id='"
                + divId + "' contenteditable='true' class='editor h1Editor sectionEditor'>" +
                "<h1>" + sectionTitle + "</h1></div>" +
                " <div id='buttonsDiv" + locationId + "' class='sectionButtons'><input type='button' id='" + toggleId + "' class='active' value='" + startEditingText + "'/>" +
                "<input type='button' id='" + removeDivId + "' class='active' value='" + deleteSectionText + "'/>" +
                "</div><ol id='addSubsection" + locationId + "' class='h2NestedLevel' style='display: block;'></ol>" +
                "<div style='padding:5px;'><input type='button' id='" + addSubsectionButtonId + "' class='active' value='" + addSubsectionButtonText + "'/></div></li>";
            $( "#addSectionDiv" ).before(html);

            // save to the db
            var actionUrl = $('#newCitationListForm').attr('action');
            $('#citation_action').val('add_section');
            var params = $('#newCitationListForm').serializeArray();
            params.push({name:'sectionType', value:'HEADING1'});
            params.push({name:'locationId', value:locationId});
            ajaxPost(actionUrl, params, true);

            // set up click handlers on buttons
            onClick( $('#' + toggleId).get(0), toggleEditor );
            onClick( $('#' + removeDivId).get(0), removeSection );
            onClick( $('#' + addSubsectionButtonId).get(0), addSubsection );

            // refresh drag and drop list
            $("ol.serialization").sortable("destroy");
            createDragAndDropList();

            increasePageHeight(150);
        });

        // handler for adding a subsection
        function addSubsection() {

            // is h1 or h2
            var isH1 = $(this).attr('id').indexOf('addH1SubsectionButton')!=-1;
            if (isH1){
                sectionType = 'HEADING1';
            }
            else {
                sectionType = 'HEADING2';
            }

            // determine location on page
            var locationId = 0;
            var h1;
            if (sectionType === 'HEADING1') {
                h1 = $(this).parent().parent();

                // add up all the previous h1's on the page and their subsections
                h1.prevAll('li[id^="link"]').each(function() {
                    locationId = locationId + 1 + $(this).find('li[id^="link"]').size();
                });

                // add in the subsections of this h1
                locationId = locationId + 1 + h1.find('li[id^="link"]').size();

                // new location
                locationId = parseInt(locationId)+1;
            }
            else if (sectionType === 'HEADING2') {
                h1 = $(this).parent().parent().parent().parent();

                // add up all the previous h1's on the page and their subsections
                h1.prevAll('li[id^="link"]').each(function() {
                    locationId = locationId + 1 + $(this).find('li[id^="link"]').size(); // add 1 for the previous h1 and then all the subsections and citations
                });

                // add up all the previous h2's in this h1 and its subsections
                var h2 = $(this).parent().parent();
                h2.prevAll('li.h2Section').each(function() {
                    locationId = locationId + 1 + $(this).find('li[id^="link"]').size(); // this is the outer shell around the link so it includes the h2 so no need to +1
                });

                // add in the subsections of this h2
                locationId = locationId + 1 + h2.find('li[id^="link"]').size();

                // add this h1 in
                locationId = locationId + 1;

                // new location
                locationId = parseInt(locationId)+1;
            }


            // create html
            var divId = 'sectionInlineEditor' + locationId;
            var toggleId = 'toggle' + locationId;
            var removeDivId = 'removeSection' + locationId;
            var sectionTitle = $('#sectionTitleText').val();
            var startEditingText = $('#startEditingText').val();
            var addSubsectionButtonId = 'addH2SubsectionButton' + locationId;
            var addSubsectionButtonText = $('#addSubsectionButtonText').val();
            var deleteSectionText = $('#deleteSectionText').val();
            var sectionType;
            var html;
            if (sectionType === 'HEADING1'){
                html =
                    "" +
                    "<li id='link" + locationId + "' data-value='<h2>" + sectionTitle + "</h2>' class='h2Section' data-location='" + locationId + "' data-sectiontype='HEADING2'>" +
                    "<div id='" + divId + "' class='editor h2Editor sectionEditor' contenteditable='true'>" + "<h2>" + sectionTitle + "</h2></div>" +
                    "<div id='buttonsDiv" + locationId + "' style='margin-left: 5px;'><input type='button' id='" + toggleId + "' class='active' value='" + startEditingText + "'/>" +
                    "<input type='button' id='" + removeDivId + "' class='active' value='" + deleteSectionText + "'/>" +
                    "</div><ol id='addSubsection" + locationId + "' class='h3NestedLevel' style='padding: 5px; display: block;'></ol><div style='padding:5px;'><input type='button' id='" + addSubsectionButtonId + "' class='active' value='" + addSubsectionButtonText + "'/></div></li>";
                $(this).parent().prevAll('ol.h2NestedLevel').show();
                $(this).parent().prevAll('ol.h2NestedLevel').append(html);
            }
            if (sectionType === 'HEADING2'){
                html =
                    "<li id='link" + locationId + "' data-value='<h3>" + sectionTitle + "</h3>' class='h3Section' data-location='" + locationId + "' data-sectiontype='HEADING3'>" +
                    "<div id='" + divId + "' class='editor h3Editor sectionEditor' contenteditable='true'>" + "<h3>" + sectionTitle + "</h3></div>" +
                    "<div id='buttonsDiv" + locationId + "' style='padding:5px;'><input type='button' id='" + toggleId + "' class='active' value='" + startEditingText + "'/>" +
                    "<input type='button' id='" + removeDivId + "' class='active' value='" + deleteSectionText + "'/>" +
                    "</div><ol class='h4NestedLevel' style='padding: 5px; display: block;'></ol></li>";
                $(this).parent().prevAll('ol.h3NestedLevel').show();
                $(this).parent().prevAll('ol.h3NestedLevel').append(html);
            }


            // set up click handlers on buttons
            onClick( $('#' + toggleId).get(0), toggleEditor );
            onClick( $('#' + removeDivId).get(0), removeSection );
            if (sectionType === 'HEADING1'){
                onClick( $('#' + addSubsectionButtonId).get(0), addSubsection );
            }

            refreshIdsOnPage();


            // save to the db
            var actionUrl = $('#newCitationListForm').attr('action');
            $('#citation_action').val('add_subsection');
            var params = $('#newCitationListForm').serializeArray();
            if (sectionType === 'HEADING1'){
                params.push({name:'addSectionHTML', value:'<h2>' + sectionTitle + '</h2>'});
                params.push({name:'sectionType', value:'HEADING2'});
            }
            else if (sectionType === 'HEADING2'){
                params.push({name:'addSectionHTML', value:'<h3>' + sectionTitle + '</h3>'});
                params.push({name:'sectionType', value:'HEADING3'});
            }
            params.push({name:'locationId', value:locationId});
            ajaxPost(actionUrl, params, true);

            $("ol.serialization").sortable("destroy");
            createDragAndDropList();

            addAccordionFunctionality(false);

            if (sectionType === 'HEADING1'){
                increasePageHeight(110);
            }
            else if (sectionType === 'HEADING2'){
                increasePageHeight(90);
            }
        }

        function increasePageHeight(heightToAdd) {
            var iFrame = $(parent.document.getElementById(window.name));
            iFrame.height(iFrame.height() + heightToAdd);
        }

        function refreshIdsOnPage() {
            var count = 1;
            $('.h1NestedLevel li[id^="link"]').each(function() {
                $(this).attr('id', 'link' + count);
                $(this).attr('data-location', count);

                $(this).find("> div").each(function() {
                    var id = $(this).attr('id');
                    if (id!=null){
                        if (id.indexOf('sectionInlineEditor')!=-1){
                            $(this).attr('id', 'sectionInlineEditor' + count);
                        }
                        else if (id.indexOf('buttonsDiv')!=-1){
                            $(this).attr('id', 'buttonsDiv' + count);
                            $(this).find("> input").each(function() {
                                var id = $(this).attr('id');
                                if (id.indexOf('toggle')!=-1){
                                    $(this).attr('id', 'toggle' + count);
                                }
                                else if (id.indexOf('removeSection')!=-1){
                                    $(this).attr('id', 'removeSection' + count);
                                }
                                else if (id.indexOf('addH1SubsectionButton')!=-1){
                                    $(this).attr('id', 'addH1SubsectionButton' + count);
                                }
                                else if (id.indexOf('addH2SubsectionButton')!=-1){
                                    $(this).attr('id', 'addH2SubsectionButton' + count);
                                }
                            });
                        }
                    }
                });
                count++;
            });
        }

        function ajaxPost(actionUrl, params, async) {
            $.ajax({
                type: 'POST',
                url: actionUrl,
                cache: false,
                async: async,
                data: params,
                dataType: 'json',
                success: function (jsObj) {
                    $.each(jsObj, function (key, value) {
                        if (key === 'message' && value && 'null' !== value && '' !== $.trim(value)) {
                            reportSuccess(value);
                        } else if(key === 'sectionToRemove') {
                            // remove section from page
                            $(value).parent().remove();
                        } else if (key === 'secondsBetweenSaveciteRefreshes') {
                            citations_new_resource.secondsBetweenSaveciteRefreshes = value;
                        } else if ($.isArray(value)) {
                            reportError('result for key ' + key + ' is an array: ' + value);
                        } else {
                            $('input[name=' + key + ']').val(value);
                        }
                    });
                },
                error: function (jqXHR, textStatus, errorThrown) {
                    reportError("failed: " + textStatus + " :: " + errorThrown);
                }
            });
        }

        function enableEditing(sectionInlineEditor) {
            if ( !CKEDITOR.instances[sectionInlineEditor] ) {
                CKEDITOR.inline( sectionInlineEditor, {
                    startupFocus: true,
                    forcePasteAsPlainText : true,
                    on:
                    {
                        instanceReady:function(event)
                        {
                            var editorText = event.editor.getData();
                            if (editorText=='<h1>Section Title</h1>\n' ||
                                editorText=='<h2>Section Title</h2>\n' ||
                                editorText=='<h3>Section Title</h3>\n' ||
                                editorText=='<p>Reading List Introduction</p>\n'){
                                $('#' + event.editor.name).children().first().text(' ');
                            }
                        }
                    },
                    toolbar :
                        [
                            { name: 'basicstyles', items : [ 'Bold','Italic', 'Underline', 'Strike' ] },
                            { name: 'styles', items : [ 'Format' ] }
                        ]
                } );
            }
        }

        function disableEditing(sectionInlineEditor) {
            if ( CKEDITOR.instances[sectionInlineEditor])
                CKEDITOR.instances[sectionInlineEditor].destroy();
        }

        function toggleEditor() {
            if ( isEditingEnabled ) {  // clicked 'Finish Editing'
                disableEditing(this.id.replace(TOGGLE, SECTION_INLINE_EDITOR));
                $('#' + this.id.replace(TOGGLE, SECTION_INLINE_EDITOR)).attr( 'contenteditable', false );
                this.value = $('#startEditingText').val();
                isEditingEnabled = false;

                // save to db
                var actionUrl = $('#newCitationListForm').attr('action');
                if (this.id === 'toggleDescription') {
                    $('#citation_action').val('update_introduction');
                }
                else {
                    $('#citation_action').val('update_section');
                }
                var params = $('#newCitationListForm').serializeArray();
                params.push({name:'addSectionHTML', value:$('#' + this.id.replace(TOGGLE, SECTION_INLINE_EDITOR)).get(0).innerHTML});
                params.push({name:'sectionType', value:$('#' + this.id.replace(TOGGLE, SECTION_INLINE_EDITOR)).parent().attr('data-sectiontype')});
                params.push({name:'locationId', value:this.id.replace(TOGGLE, "")});

                ajaxPost(actionUrl, params, true);

                // re enable drag and drop
                $("ol.serialization").sortable("enable"); //call widget-function enable
            }
            else { // clicked 'Edit'
                $('#' + this.id.replace(TOGGLE, SECTION_INLINE_EDITOR)).attr( 'contenteditable', true );
                enableEditing(this.id.replace(TOGGLE, SECTION_INLINE_EDITOR));
                this.value = $('#finishEditingText').val();
                isEditingEnabled = true;

                // disable drag and drop while editing
                $("ol.serialization").sortable("disable"); //call widget-function disable
            }
        }

        function removeSection() {
            var confirmMessage = $('#deleteButtonConfirmText').val();
            if(confirm(confirmMessage)) {
                var actionUrl = $('#newCitationListForm').attr('action');
                $('#citation_action').val('remove_section');
                var params = $('#newCitationListForm').serializeArray();
                params.push({name: 'locationId', value: this.id.replace('removeSection', '')});
                ajaxPost(actionUrl, params, false);

                refreshIdsOnPage();
            }
        }


        function onClick( element, callback ) {
            if ( window.addEventListener ) {
                element.addEventListener( 'click', callback, false );
            }
            else if ( window.attachEvent ) {
                element.attachEvent( 'onclick', callback );
            }
        }

        function createDragAndDropList() {

            //create drag and drop list
            var group = $("ol.serialization").sortable({
                group: 'serialization',
                revert: true,
                delay: 0,
                isValidTarget: function (item, container) {
                    var sectiontype = item.data('sectiontype');
                    if (sectiontype=='CITATION'){
                        if (container.target.hasClass("holdCitations")){
                            return true;
                        }
                        else {
                            return false;
                        }
                    }
                    else if (sectiontype=='HEADING3'){
                        if (container.target.hasClass("h3NestedLevel")){
                            return true;
                        }
                        else {
                            return false;
                        }
                    }
                    else if (sectiontype=='HEADING2'){
                        if (container.target.hasClass("h2NestedLevel")){
                            return true;
                        }
                        else {
                            return false;
                        }
                    }
                    else if (sectiontype=='HEADING1'){
                        if (container.target.hasClass("h1NestedLevel")){
                            return true;
                        }
                        else {
                            return false;
                        }
                    }

                    return true;
                },
                onDrop: function (item, container, _super) {

                    // put the editor's value in the link data-value
                    $('li[id^="link"]').each(function( ) {
                        var editorId = $(this).attr('id').replace('link', 'sectionInlineEditor');
                        var outerHTML = '';
                        if ($('#' + editorId).html()!=null){
                            outerHTML = $('#' + editorId).html().trim();
                        }
                        $(this).data('value', outerHTML);
                    });

                    // if it's a citation dropped into nest then increase page height
                    var sectiontype = item.data('sectiontype');
                    if (sectiontype=='CITATION'){
                        increasePageHeight(70);
                    }

                    // if it's a citation being moved upwards then show the div and buttons
                    item.children().each(function( ) {
                        $(this).show();
                    });

                    // save to the db
                    var actionUrl = $('#newCitationListForm').attr('action');
                    $('#citation_action').val('drag_and_drop');
                    var params = $('#newCitationListForm').serializeArray();
                    params.push({name:'sectionType', value:item.data('sectiontype')});
                    var data = group.sortable("serialize").get()[0];
                    var jsonString = JSON.stringify(data, null, ' ');
                    params.push({name:'data', value:jsonString});

                    ajaxPost(actionUrl, params, true);

                    refreshIdsOnPage();

                    _super(item, container);
                }
            });
        }

        function addAccordionFunctionality(collapseAllSections) {

            // remove any bound click events
            $('.h1NestedLevel li[data-sectiontype="HEADING1"] > div[id^=sectionInlineEditor]').unbind("click");
            $('.h2NestedLevel li[data-sectiontype="HEADING2"] > div[id^=sectionInlineEditor]').unbind("click");
            $('.h3NestedLevel li[data-sectiontype="HEADING3"] > div[id^=sectionInlineEditor]').unbind("click");


            // h1 level collapse expand
            if (collapseAllSections) {
                $('.h1NestedLevel ol').each(function () {
                    $(this).hide();
                });
            }

            $('.h1NestedLevel li[data-sectiontype="HEADING1"] > div[id^=sectionInlineEditor]').click(function() {
                $(this).parent().find('ol').slideToggle();
            });

            // h2 level collapse expand
            if (collapseAllSections) {
                $('.h2NestedLevel ol').each(function () {
                    $(this).hide();
                });
            }

            $('.h2NestedLevel li[data-sectiontype="HEADING2"] > div[id^=sectionInlineEditor]').click(function() {
                $(this).parent().find('ol').slideToggle();
            });

            // h3 level collapse expand
            if (collapseAllSections) {
                $('.h3NestedLevel ol').each(function () {
                $(this).hide();
                });
            }

            $('.h3NestedLevel li[data-sectiontype="HEADING3"] > div[id^=sectionInlineEditor]').click(function() {
                $(this).parent().find('ol').slideToggle();
            });
        }


        createDragAndDropList();

        toggle.each(function( ) {
            onClick( this, toggleEditor );
        });
        removeButton.each(function( ) {
            onClick( this, removeSection);
        });
        addH1SubsectionButton.each(function( ) {
            onClick( this, addSubsection);
        });
        addH2SubsectionButton.each(function( ) {
            onClick( this, addSubsection);
        });

        addAccordionFunctionality(true);

        $('.unnestedList').each(function( ) {
            $(this).show();
        });

        if ( !$.browser.mozilla ) {
            alert('You should not edit this page in any browser other than Firefox - as you are likely to lose your data.');
        }
    });
}());

function addSectionHeightsToPageHeight(numSections) {
    var height = (numSections-1) * 100;
    window.onload = function() {
        var iFrame = $(parent.document.getElementById(window.name));
        iFrame.height(iFrame.height() + height);
    }
}
