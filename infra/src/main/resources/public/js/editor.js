window.RTE = (function(){
	return {
		Instance: function(data){
			var that = this;
			this.toolbar = new RTE.Toolbar(this);

			function getSelectedElements(){
				var selection = getSelection();
				if(!selection.rangeCount || !that.element.children('[contenteditable]').is(':focus')){
					return $();
				}
				var range = selection.getRangeAt(0);
				that.range = range;
				var jSelector = $();
				var iterator = $(range.startContainer.parentElement);
				jSelector = jSelector.add(iterator);
				var end = $(range.endContainer.parentElement);
				while((iterator[0] && end[0]) && iterator[0] !== end[0] && !iterator.find(end[0]).length){
					iterator = iterator.next();
					jSelector = jSelector.add(iterator);
				}
				return jSelector;
			}

			this.selection = getSelectedElements();

			this.focus = function(){
				var sel = window.getSelection();
				sel.removeAllRanges();
				sel.addRange(this.range);
			};

			this.insertHTML = function(htmlContent){
				var wrapper = document.createElement('div');
				$(wrapper).html(htmlContent);
				if(this.range){
					this.range.insertNode(wrapper);
				}
				else{
					this.element.find('[contenteditable]').append(wrapper);
				}
				this.trigger('contentupdated');
			};

			function selectionChanged(){
				var selectedElements = getSelectedElements();

				if(that.selection.length !== selectedElements.length){
					that.selection = selectedElements;
					return true;
				}

				var same = true;
				that.selection.each(function(index, item){
					if(selectedElements[index] !== item){
						same = false;
					}
				});

				if(!same){
					that.selection = selectedElements;
				}
				return !same;
			}

			$('body').on('mouseup', function(){
				if(!selectionChanged()){
					return;
				}
				that.trigger('selectionchange', {
					selection: new RTE.Selection({
						selectedElements: getSelectedElements()
					})
				});
			});

			data.element.on('keyup', function(e){
				that.trigger('contentupdated');
				if(!selectionChanged()){
					return;
				}
				that.trigger('selectionchange', {
					selection: new RTE.Selection({
						selectedElements: getSelectedElements()
					})
				});
			});
		},
		Toolbar: function(instance){
			instance.toolbarConfiguration.options.forEach(function(option){
				var optionElement = $('<div></div>');
				optionElement.addClass('option');
				optionElement.addClass(option.name.replace(/([A-Z])/g, "-$1").toLowerCase());
				instance.element.find('editor-toolbar').append(optionElement);
				var optionScope = instance.scope.$new();

				var optionResult = option.run(instance);
				optionElement.html(instance.compile(optionResult.template)(optionScope));
				optionResult.link(optionScope, optionElement, instance.attributes);
			});
		},
		ToolbarConfiguration: function(){
			this.collection(RTE.Option);
			this.option = function(name, fn){
				this.options.push({
					name: name,
					run: fn
				});
			};
		},
		Option: function(){

		},
		Selection: function(){

		},
		setModel: function(){
			model.makeModels(RTE);
			RTE.baseToolbarConf = new RTE.ToolbarConfiguration();
		},
		addDirectives: function(module){
			this.setModel();

			// Editor options
			RTE.baseToolbarConf.option('undo', function(instance){
				return {
					template: '<i></i>',
					link: function(scope, element, attributes){
						element.on('click', function(){
							document.execCommand('undo');
						});
					}
				};
			});

			RTE.baseToolbarConf.option('redo', function(instance){
				return {
					template: '<i></i>',
					link: function(scope, element, attributes){
						element.on('click', function(){
							document.execCommand('redo');
						});
					}
				};
			});

			RTE.baseToolbarConf.option('bold', function(instance){
				return {
					template: '<i></i>',
					link: function(scope, element, attributes){
						element.on('click', function(){
							document.execCommand('bold');
							if(document.queryCommandState('bold')){
								element.addClass('toggled');
							}
							else{
								element.removeClass('toggled');
							}
						});

						instance.on('selectionchange', function(e){
							if(document.queryCommandState('bold')){
								element.addClass('toggled');
							}
							else{
								element.removeClass('toggled');
							}
						});
					}
				};
			});

			RTE.baseToolbarConf.option('italic', function(instance){
				return {
					template: '<i></i>',
					link: function(scope, element, attributes){
						element.on('click', function(){
							document.execCommand('italic');
							if(document.queryCommandState('italic')){
								element.addClass('toggled');
							}
							else{
								element.removeClass('toggled');
							}
						});

						instance.on('selectionchange', function(e){
							if(document.queryCommandState('italic')){
								element.addClass('toggled');
							}
							else{
								element.removeClass('toggled');
							}
						});
					}
				};
			});

			RTE.baseToolbarConf.option('underline', function(instance){
				return {
					template: '<i></i>',
					link: function(scope, element, attributes){
						element.on('click', function(){
							document.execCommand('underline');
							if(document.queryCommandState('underline')){
								element.addClass('toggled');
							}
							else{
								element.removeClass('toggled');
							}
						});

						instance.on('selectionchange', function(e){
							if(document.queryCommandState('underline')){
								element.addClass('toggled');
							}
							else{
								element.removeClass('toggled');
							}
						});
					}
				};
			});

			RTE.baseToolbarConf.option('removeFormat', function(instance){
				return {
					template: '<i></i>',
					link: function(scope, element, attributes){
						element.on('click', function(){
							document.execCommand('removeFormat');
							if(document.queryCommandState('removeFormat')){
								element.removeClass('disabled');
							}
							else{
								element.addClass('disabled');
							}
						});

						instance.on('selectionchange', function(e){
							if(document.queryCommandState('removeFormat')){
								element.removeClass('disabled');
							}
							else{
								element.addClass('disabled');
							}
						});
					}
				};
			});

			RTE.baseToolbarConf.option('justifyLeft', function(instance){
				return {
					template: '<i></i>',
					link: function(scope, element, attributes){
						element.addClass('toggled');
						element.on('click', function(){
							document.execCommand('justifyLeft');
							if(document.queryCommandState('justifyLeft')){
								element.addClass('toggled');
							}
							else{
								element.removeClass('toggled');
							}
						});

						instance.on('selectionchange', function(e){
							if(document.queryCommandState('justifyLeft')){
								element.addClass('toggled');
							}
							else{
								element.removeClass('toggled');
							}
						});
					}
				};
			});

			RTE.baseToolbarConf.option('justifyRight', function(instance){
				return {
					template: '<i></i>',
					link: function(scope, element, attributes){
						element.on('click', function(){
							document.execCommand('justifyRight');
							if(document.queryCommandState('justifyRight')){
								element.addClass('toggled');
							}
							else{
								element.removeClass('toggled');
							}
						});

						instance.on('selectionchange', function(e){
							if(document.queryCommandState('justifyRight')){
								element.addClass('toggled');
							}
							else{
								element.removeClass('toggled');
							}
						});
					}
				};
			});

			RTE.baseToolbarConf.option('justifyCenter', function(instance){
				return {
					template: '<i></i>',
					link: function(scope, element, attributes){
						element.on('click', function(){
							document.execCommand('justifyCenter');
							if(document.queryCommandState('justifyCenter')){
								element.addClass('toggled');
							}
							else{
								element.removeClass('toggled');
							}
						});

						instance.on('selectionchange', function(e){
							if(document.queryCommandState('justifyCenter')){
								element.addClass('toggled');
							}
							else{
								element.removeClass('toggled');
							}
						});
					}
				};
			});

			RTE.baseToolbarConf.option('justifyFull', function(instance){
				return {
					template: '<i></i>',
					link: function(scope, element, attributes){
						element.on('click', function(){
							document.execCommand('justifyFull');
						});
					}
				};
			});

			RTE.baseToolbarConf.option('subscript', function(instance){
				return {
					template: '<i></i>',
					link: function(scope, element, attributes){
						element.on('click', function(){
							document.execCommand('justifyFull');
							if(document.queryCommandState('justifyFull')){
								element.addClass('toggled');
							}
							else{
								element.removeClass('toggled');
							}
						});

						instance.on('selectionchange', function(e){
							if(document.queryCommandState('justifyFull')){
								element.addClass('toggled');
							}
							else{
								element.removeClass('toggled');
							}
						});
					}
				};
			});

			RTE.baseToolbarConf.option('superscript', function(instance){
				return {
					template: '<i></i>',
					link: function(scope, element, attributes){
						element.on('click', function(){
							document.execCommand('superscript');
							if(document.queryCommandState('superscript')){
								element.addClass('toggled');
							}
							else{
								element.removeClass('toggled');
							}
						});

						instance.on('selectionchange', function(e){
							if(document.queryCommandState('superscript')){
								element.addClass('toggled');
							}
							else{
								element.removeClass('toggled');
							}
						});
					}
				};
			});

			RTE.baseToolbarConf.option('ulist', function(instance){
				return {
					template: '<i></i>',
					link: function(scope, element, attributes){
						element.on('click', function(){
							document.execCommand('insertUnorderedList');
							if(document.queryCommandState('insertUnorderedList')){
								element.addClass('toggled');
							}
							else{
								element.removeClass('toggled');
							}
						});

						instance.on('selectionchange', function(e){
							if(document.queryCommandState('insertUnorderedList')){
								element.addClass('toggled');
							}
							else{
								element.removeClass('toggled');
							}
						});
					}
				};
			});

			RTE.baseToolbarConf.option('olist', function(instance){
				return {
					template: '<i></i>',
					link: function(scope, element, attributes){
						element.on('click', function(){
							document.execCommand('insertOrderedList');
							if(document.queryCommandState('insertOrderedList')){
								element.addClass('toggled');
							}
							else{
								element.removeClass('toggled');
							}
						});

						instance.on('selectionchange', function(e){
							if(document.queryCommandState('insertOrderedList')){
								element.addClass('toggled');
							}
							else{
								element.removeClass('toggled');
							}
						});
					}
				};
			});

			RTE.baseToolbarConf.option('color', function(instance){
				return {
					template: '<input type="color" />',
					link: function(scope, element, attributes){
						scope.foreColor = "#000000";
						element.children('input').on('change', function(){
							scope.foreColor = $(this).val();
							scope.$apply('foreColor');
						});

						scope.$watch('foreColor', function(){
							document.execCommand('foreColor', false, scope.foreColor);
						});

						instance.on('selectionchange', function(e){
							scope.foreColor = document.queryCommandValue('foreColor');
							scope.$apply();
						});
					}
				};
			});

			RTE.baseToolbarConf.option('backgroundColor', function(instance){
				return {
					template: '<input type="color" />',
					link: function(scope, element, attributes){
						element.children('input').on('change', function(){
							scope.backColor = $(this).val();
							scope.$apply('backColor');
						});

						scope.$watch('backColor', function(){
							document.execCommand('backColor', false, scope.backColor);
						});

						instance.on('selectionchange', function(e){
							scope.backColor = document.queryCommandValue('backColor');
							scope.$apply();
						});
					}
				};
			});

			RTE.baseToolbarConf.option('font', function(instance){
				return {
					template:
						'<select-list ng-model="font" display-as="fontFamily" placeholder="Police">' +
							'<option ng-repeat="font in fonts" value="font" style="font-family: [[font.fontFamily]]">[[font.fontFamily]]</option>' +
						'</select-list>',
					link: function(scope, element, attributes){
						var importedFonts =
							_.map(
								_.flatten(
									_.map(
										document.styleSheets,
										function(stylesheet){
											return _.filter(
												stylesheet.cssRules,
												function(cssRule){
													return cssRule instanceof CSSFontFaceRule &&
														cssRule.style.fontFamily.toLowerCase().indexOf('fontello') === -1 &&
														cssRule.style.fontFamily.toLowerCase().indexOf('glyphicon') === -1 &&
														cssRule.style.fontFamily.toLowerCase().indexOf('fontawesome') === -1;
												}
											)
										}
									)
								),
								function(fontFace){
									return {
										fontFamily: fontFace.style.fontFamily
									}
								}
							);
						scope.fonts = [{ fontFamily: 'Arial' }, { fontFamily: 'Verdana' }, { fontFamily: 'Tahoma' }, { fontFamily: 'Comic Sans MS' }].concat(importedFonts);
						scope.font = _.findWhere(scope.fonts, { fontFamily: $('p').css('font-family') });
					}
				};
			});

			RTE.baseToolbarConf.option('fontSize', function(instance) {
				return {
					template: '<select-list ng-model="fontSize" placeholder="Taille">' +
					'<option ng-repeat="fontSize in fontSizes" value="font" style="font-size: [[fontSize]]">[[fontSize]]</option>' +
					'</select-list>',
					link: function(scope, element, attributes){
						scope.fontSizes = [8,10,12,14,16,18,20,24,28,34,42,64,72];
					}
				}
			});

			RTE.baseToolbarConf.option('image', function(instance){
				return {
					template: '<i></i>' +
						'<lightbox show="display.pickFile" on-close="display.pickFile = false;">' +
							'<media-library ng-change="updateContent()" ng-model="display.file" file-format="\'img\'"></media-library>' +
						'</lightbox>',
					link: function(scope, element, attributes){
						instance.element.children('[contenteditable]').addClass('drawing-zone');
						scope.display = {};
						scope.updateContent = function(){
							document.execCommand('insertImage', false, '/workspace/document/' + scope.display.file._id);
							scope.display.pickFile = false;
							instance.element.children('[contenteditable]').find('img').each(function(index, item){
								if($(item).attr('src') === '/workspace/document/' + scope.display.file._id){
									$(item).attr('draggable', true);
									$(item).attr('native', true);

									instance.element.on('drop', function(e){
										ui.extendElement.resizable($(item), { moveWithResize: false });
									});
								}
							});
						};

						element.children('i').on('click', function(){
							scope.display.pickFile = true;
							scope.$apply('display');
						})
					}
				}
			});

			RTE.baseToolbarConf.option('linker', function(instance){
				return {
					template: '<i ng-click="display.chooseLink = true"></i>' +
					'<div ng-include="\'/infra/public/template/linker.html\'"></div>',
					link: function(scope, element, attributes){
						scope.linker = {
							display: {},
							apps: [],
							search: {
								application: {}
							},
							params: {},
							resource: {}
						};

						scope.linker.loadApplicationResources = function(cb){
							var split = scope.linker.search.application.address.split('/');
							var prefix = split[split.length - 1];
							scope.linker.params.appPrefix = prefix;
							if(!cb){
								cb = function(){
									scope.linker.searchApplication();
									scope.$apply('linker');
								};
							}
							Behaviours.applicationsBehaviours[prefix].loadResources(cb);
							scope.linker.addResource = Behaviours.applicationsBehaviours[prefix].create;
						};

						scope.linker.searchApplication = function(cb){
							var split = scope.linker.search.application.address.split('/');
							var prefix = split[split.length - 1];
							scope.linker.params.appPrefix = prefix;
							Behaviours.loadBehaviours(scope.linker.params.appPrefix, function(appBehaviour){
								scope.linker.resources = _.filter(appBehaviour.resources, function(resource) {
									return scope.linker.search.text !== '' && (lang.removeAccents(resource.title.toLowerCase()).indexOf(lang.removeAccents(scope.linker.search.text).toLowerCase()) !== -1 ||
											resource._id === scope.linker.search.text);
								});
								scope.linker.resource.title = scope.linker.search.text;
								if(typeof cb === 'function'){
									cb();
								}
							});
						};

						scope.linker.createResource = function(){
							Behaviours.loadBehaviours(scope.linker.params.appPrefix, function(appBehaviour){
								appBehaviour.create(scope.linker.resource, function(){
									scope.linker.searchApplication();
									scope.linker.search.text = scope.linker.resource.title;
									scope.$apply();
								});
							});
						};

						scope.linker.applyLink = function(link){
							scope.linker.params.link = link;
						};

						scope.linker.applyResource = function(resource){
							scope.linker.params.link = resource.path;
							scope.linker.params.id = resource._id;
						};

						scope.linker.saveLink = function(){
							if(scope.linker.params.blank){
								scope.linker.params.target = '_blank';
							}

							var linkNode = $('<a></a>');
							if(scope.linker.params.link){
								linkNode.attr('href', scope.linker.params.link);

								if(scope.linker.params.appPrefix){
									linkNode.attr('data-app-prefix', scope.linker.params.appPrefix);
									if(scope.linker.params.appPrefix !== 'workspace' && !scope.linker.externalLink){
										linkNode.data('reload', true);
									}
								}
								if(scope.linker.params.id){
									linkNode.attr('data-id', scope.linker.params.id);
								}
								if(scope.linker.params.blank){
									scope.linker.params.target = '_blank';
									linkNode.attr('target', scope.linker.params.target);
								}
								if(scope.linker.params.tooltip){
									linkNode.attr('tooltip', scope.linker.params.tooltip);
								}
							}

							instance.focus();

							if(instance.selection.length === 0){
								linkNode.text(scope.linker.params.link);
								instance.insertHTML(linkNode[0].outerHTML);
							}
							else{
								document.execCommand('createLink', false, scope.linker.params.link);
								instance.element.children('[contenteditable]').find('a').each(function(index, item){
									for(var i = 0; i < linkNode[0].attributes.length; i++){
										$(item).attr(linkNode[0].attributes[i].nodeName, linkNode[0].attributes[i].nodeValue);
									}
								});
							}

							scope.display.chooseLink = false;
						};

						scope.linker.cancel = function(){
							scope.display.chooseLink = false;
						};

						http().get('/resources-applications').done(function(apps){
							scope.linker.apps = _.filter(model.me.apps, function(app){
								return _.find(
									apps,
									function(match){
										return app.address.indexOf(match) !== -1 && app.icon
									}
								);
							});

							scope.linker.search.application = _.find(scope.linker.apps, function(app){ return app.address.indexOf(appPrefix) !== -1 });
							if(!scope.linker.search.application){
								scope.linker.search.application = scope.linker.apps[0];
								scope.linker.searchApplication(function(){
									scope.linker.loadApplicationResources(function(){});
								})
							}
							else{
								scope.linker.loadApplicationResources(function(){});
							}

							scope.$apply('linker');
						});
					}
				}
			});

			RTE.baseToolbarConf.option('unlink', function(instance){
				return {
					template: '<i></i>',
					link: function(scope, element, attributes){
						element.addClass('disabled');
						element.on('click', function(){
							document.execCommand('unlink');
							element.addClass('disabled');
						});

						instance.on('selectionchange', function(e){
							if(e.selection.selectedElements.is('a')){
								element.removeClass('disabled');
							}
							else{
								element.addClass('disabled');
							}
						});
					}
				};
			});

			RTE.baseToolbarConf.option('smileys', function(instance){
				return {
					template: '' +
						'<i></i>' +
						'<lightbox show="display.pickSmiley" on-close="display.pickSmiley = false;">' +
							'<h2>Insérer un smiley</h2>' +
							'<div class="row">' +
								'<i ng-repeat="smiley in smileys" ng-click="addSmiley(smiley)">[[smiley]]</i>' +
							'</div>' +
						'</lightbox>',
					link: function(scope, element, attributes){
						scope.display = {};
						scope.smileys = [ "happy", "proud", "dreamy", "love", "tired", "angry", "worried", "sick", "joker", "sad" ];
						scope.addSmiley = function(smiley){
							var content = '<i class="' + smiley + '"></i>';
							instance.insertHTML(content);
							scope.display.pickSmiley = false;
						}

						element.addClass('disabled');
						element.on('click', function(){
							scope.display.pickSmiley = true;
						});
					}
				};
			});

			RTE.baseToolbarConf.option('table', function(instance){
				return {
					template: '' +
					'<popover>' +
						'<i popover-opener opening-event="click"></i>' +
						'<popover-content>' +
							'<div class="draw-table"></div>' +
						'</popover-content>' +
					'</popover>',
					link: function(scope, element, attributes){
						var nbRows = 12;
						var nbCells = 12;
						var drawer = element.find('.draw-table');
						for(var i = 0; i < nbRows; i++){
							var line = $('<div class="row"></div>');
							drawer.append(line);
							for(var j = 0; j < nbCells; j++){
								line.append('<div class="one cell"></div>');
							}
						}

						drawer.find('.cell').on('mouseover', function(){
							var line = $(this).parent();
							for(var i = 0; i <= line.index(); i++){
								var row = $(drawer.find('.row')[i]);
								for(var j = 0; j <= $(this).index(); j++){
									var cell = $(row.find('.cell')[j]);
									cell.addClass('match');
								}
							}
						});

						drawer.find('.cell').on('mouseout', function(){
							drawer.find('.cell').removeClass('match');
						});

						drawer.find('.cell').on('click', function(){
							var table = document.createElement('table');
							var line = $(this).parent();
							for(var i = 0; i <= line.index(); i++){
								var row = $('<tr></tr>');
								$(table).append(row);
								for(var j = 0; j <= $(this).index(); j++){
									var cell = $('<td></td>');
									cell.html('&nbsp;')
									row.append(cell);
								}
							}
							instance.insertHTML(table.outerHTML);
							element.find('popover-content').addClass('hidden');
						});
					}
				}
			});

			RTE.baseToolbarConf.option('templates', function(instance){
				return {
					template: '<i></i>' +
					'<lightbox show="display.pickTemplate" on-close="display.pickTemplate = false;">' +
						'<h2>Choisir un modèle</h2>' +
						'<ul class="thought-out-actions">' +
							'<li ng-repeat="template in templates" ng-click="applyTemplate(template)">[[template.title]]</li>' +
						'</ul>' +
					'</lightbox>',
					link: function(scope, element, attributes){
						scope.templates = [
							{
								title: 'Page blanche',
								html: '<p></p>'
							},
							{
								title: 'Deux colonnes',
								html:
									'<div class="row">' +
										'<article class="six cell">' +
											'<h2>Titre de votre première colonne</h2>' +
											'<p>Vous pouvez entrer ici le texte de votre première colonne</p>' +
										'</article>' +
										'<article class="six cell">' +
											'<h2>Titre de votre deuxième colonne</h2>' +
											'<p>Vous pouvez entrer ici le texte de votre deuxième colonne</p>' +
										'</article>' +
									'</div>'
							},
							{
								title: 'Trois colonnes',
								html:
									'<div class="row">' +
										'<article class="four cell">' +
											'<h2>Titre de votre première colonne</h2>' +
											'<p>Vous pouvez entrer ici le texte de votre première colonne</p>' +
										'</article>' +
										'<article class="four cell">' +
											'<h2>Titre de votre deuxième colonne</h2>' +
											'<p>Vous pouvez entrer ici le texte de votre deuxième colonne</p>' +
										'</article>' +
										'<article class="four cell">' +
											'<h2>Titre de votre troisième colonne</h2>' +
											'<p>Vous pouvez entrer ici le texte de votre troisième colonne</p>' +
										'</article>' +
									'</div>'
							},
							{
								title: 'Illustration et texte',
								html:
									'<div class="row">' +
										'<article class="three cell">' +
											'<img skin-src="/img/illustrations/default-image.png" />' +
										'</article>' +
										'<article class="nine cell">' +
											'<h2>Titre de votre texte</h2>' +
											'<p>Vous pouvez entrer ici votre texte. Pour changer l\'image du modèle, cliquez sur l\'image, puis sur le bouton' +
											'"Insérer une image" dans la barre de boutons de l\'éditeur.</p>' +
										'</article>' +
									'</div>'
							},
							{
								title: 'Vignettes'
							}
						];
						scope.display = {};
						scope.applyTemplate = function(template){
							scope.display.pickTemplate = false;
							instance.insertHTML(_.findWhere(scope.templates, { title: template.title}).html);
							ui.extendElement.resizable(instance.element.find('article'), { moveWithResize: false });
						};

						element.children('i').on('click', function(){
							scope.display.pickTemplate = true;
							scope.$apply('display');
						});
					}
				}
			});


			//Editor
			module.directive('editor', function($parse, $compile){
				return {
					restrict: 'E',
					template: '' +
					'<editor-toolbar></editor-toolbar>' +
					'<popover>' +
						'<i class="tools" popover-opener opening-event="click"></i>' +
						'<popover-content>' +
							'<ul>' +
								'<li>Editeur de texte</li>' +
								'<li>Code HTML</li>' +
								'<li>Mode mixte</li>' +
							'</ul>' +
						'</popover-content>' +
					'</popover>' +
					'<div contenteditable="true"></div>' +
					'<textarea></textarea>',
					link: function(scope, element, attributes){
						element.addClass('edit');
						var editZone = element.children('[contenteditable=true]');
						var htmlZone = element.children('textarea');
						document.execCommand('styleWithCSS', true);
						var toolbarConf = RTE.baseToolbarConf;
						if(attributes.toolbarConf){
							toolbarConf = scope.$eval(attributes.toolbarConf);
						}

						var editorInstance = new RTE.Instance({
							toolbarConfiguration: toolbarConf,
							element: element,
							scope: scope,
							compile: $compile
						});

						var ngModel = $parse(attributes.ngModel);
						if(!ngModel(scope)){
							ngModel.assign(scope, '');
						}

						scope.$watch(
							function(){
								return ngModel(scope);
							},
							function(newValue){
								if(newValue !== editZone.html()){
									editZone.html(newValue);
								}
								if(newValue !== htmlZone.val()){
									if(window.html_beautify){
										htmlZone.val(html_beautify(newValue));
									}
								}
								element.children('[contenteditable]').find('img, table').each(function(index, item){
									ui.extendElement.resizable($(item), { moveWithResize: false });
								});
							}
						);

						element.on('dragenter', function(e){
							e.preventDefault();
						});

						element.on('dragover', function(e){
							e.preventDefault();
						});

						element.children('popover').find('li:first-child').on('click', function(){
							element.removeClass('html');
							element.removeClass('both');
							element.addClass('edit');
						});

						element.children('popover').find('li:nth-child(2)').on('click', function(){
							element.removeClass('edit');
							element.removeClass('both');
							element.addClass('html');
							if(window.html_beautify){
								return;
							}
							http().get('/infra/public/js/beautify-html.js').done(function(content){
								eval(content);
								htmlZone.val(html_beautify(ngModel(scope)));
							});
						});

						element.children('popover').find('li:nth-child(3)').on('click', function(){
							element.removeClass('edit');
							element.removeClass('html');
							element.addClass('both');
							if(window.html_beautify){
								return;
							}
							http().get('/infra/public/js/beautify-html.js').done(function(content){
								eval(content);
								htmlZone.val(html_beautify(ngModel(scope)));
							});
						});

						element.find('.option i').click(function(){
							editZone.focus();
							scope.$apply(function(){
								scope.$eval(attributes.ngChange);
								ngModel.assign(scope, editZone.html());
							});
						});

						editorInstance.on('contentupdated', function(){
							htmlZone.css('min-height', editZone.height() + 'px');
							scope.$apply(function(){
								scope.$eval(attributes.ngChange);
								ngModel.assign(scope, editZone.html());
								var newHeight = htmlZone[0].scrollHeight + 2;
								if(newHeight > htmlZone.height()){
									htmlZone.height(newHeight);
								}

								editZone.css('min-height', htmlZone[0].scrollHeight + 2 + 'px');
							});
						});

						editZone.on('keydown', function(e){
							if(e.keyCode === 9){
								e.preventDefault();
								var currentTag;
								if(editorInstance.range.startContainer.tagName){
									currentTag = editorInstance.range.startContainer;
								}
								else{
									currentTag = editorInstance.range.startContainer.parentElement;
								}
								if(currentTag.tagName === 'TD'){
									var selection = window.getSelection();
									var range = document.createRange();
									range.setStart(currentTag.nextSibling, 0);
								}

								document.execCommand('indent');
							}
						});

						htmlZone.on('keyup', function(){
							var newHeight = htmlZone[0].scrollHeight + 2;
							if(newHeight > htmlZone.height()){
								htmlZone.height(newHeight);
							}
							if(newHeight > parseInt(editZone.css('min-height'))){
								editZone.css('min-height', newHeight);
							}

							scope.$apply(function(){
								scope.$eval(attributes.ngChange);
								ngModel.assign(scope, htmlZone.val());
							});
						});
					}
				}
			});


			//Style directives

			module.directive('selectList', function(){
				return {
					restrict: 'E',
					transclude: true,
					scope: {
						ngModel: '=',
						displayAs: '@',
						placeholder: '@',
						ngChange: '&'
					},
					template: '' +
						'<div class="selected-value">[[showValue()]]</div>' +
						'<div class="options hidden" ng-transclude></div>',
					link: function(scope, element, attributes){
						if(scope.default){
							scope.ngModel = scope.$eval(scope.default);
						}

						scope.showValue = function(){
							if(!scope.ngModel){
								return scope.placeholder;
							}
							if(!scope.displayAs){
								return scope.ngModel;
							}
							return scope.ngModel[scope.displayAs];
						};

						element.children('.selected-value').on('click', function(){
							if(element.children('.options').hasClass('hidden')){
								element.children('.options').removeClass('hidden');
								element.children('.options').height(element.children('.options')[0].scrollHeight);
							}
							else{
								element.children('.options').addClass('hidden');
							}
						});
						element.children('.options').on('click', 'option', function(){
							scope.ngModel = angular.element(this).scope().$eval($(this).attr('value'));
							scope.ngChange();
							element.children('.options').addClass('hidden');
							scope.$apply('ngModel');
						});

						$('body').click(function(e){
							if(element.find(e.originalEvent.target).length){
								return;
							}

							element.children('.options').addClass('hidden');
						});
					}
				}
			});

			module.directive('popover', function(){
				return {
					controller: function(){},
					restrict: 'E',
					link: function (scope, element, attributes) {

					}
				};
			});

			module.directive('popoverOpener', function(){
				return {
					require: '^popover',
					link: function(scope, element, attributes){
						var parentElement = element.parents('popover');
						var popover = parentElement.find('popover-content');
						if(attributes.openingEvent === 'click'){
							element.on('click', function(){
								if(!popover.hasClass('hidden')){
									popover.addClass("hidden");
								}
								else{
									popover.removeClass("hidden");
								}
							});

							$('body').on('click', function(e){
								if(parentElement.find(e.originalEvent.target).length > 0){
									return;
								}
								popover.addClass("hidden");
							});
						}
						else{
							parentElement.on('mouseover', function(e){
								popover.removeClass("hidden");
							});
							parentElement.on('mouseout', function(e){
								popover.addClass("hidden");
							});
						}
					}
				};
			});

			module.directive('popoverContent', function(){
				return {
					require: '^popover',
					restrict: 'E',
					link: function(scope, element, attributes){
						element.addClass("hidden");
					}
				};
			});
		}
	};
}());