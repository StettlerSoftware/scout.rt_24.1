scout.Button = function() {
  scout.Button.parent.call(this);
  this._$label;
  this._addAdapterProperties('menus');

  this.buttonKeyStroke = new scout.ButtonKeyStroke(this, null);
};
scout.inherits(scout.Button, scout.FormField);

scout.Button.SystemType = {
  NONE: 0,
  CANCEL: 1,
  CLOSE: 2,
  OK: 3,
  RESET: 4,
  SAVE: 5,
  SAVE_WITHOUT_MARKER_CHANGE: 6
};

scout.Button.DisplayStyle = {
  DEFAULT: 0,
  TOGGLE: 1,
  RADIO: 2,
  LINK: 3
};

scout.Button.prototype._init = function(model) {
  scout.Button.parent.prototype._init.call(this, model);
  this.buttonKeyStroke.parseAndSetKeyStroke(this.keyStroke);
};

/**
 * @override ModelAdapter
 */
scout.Button.prototype._initKeyStrokeContext = function(keyStrokeContext) {
  scout.Button.parent.prototype._initKeyStrokeContext.call(this, keyStrokeContext);

  keyStrokeContext.registerKeyStroke([
    new scout.ButtonKeyStroke(this, 'ENTER'),
    new scout.ButtonKeyStroke(this, 'SPACE')
  ]);
};

/**
 * The button form-field has no label and no status. Additionally it also has no container.
 * Container and field are the same thing.
 */
scout.Button.prototype._render = function($parent) {
  var cssClass, $button;
  if (this.displayStyle === scout.Button.DisplayStyle.LINK) {
    /* Render as link-button/ menu-item.
     * This is a bit weird: the model defines a button, but in the UI it behaves like a menu-item.
     * Probably it would be more reasonable to change the configuration (which would lead to additional
     * effort required to change an existing application).
     */
    $button = $('<div>');
    $button.setTabbable(this.enabled);
    $button.addClass('menu-item');
    cssClass = 'link-button';
  } else {
    // render as button
    $button = $('<button>');
    cssClass = 'button';
  }
  this._$label = $button.appendSpan('button-label');
  this.addContainer($parent, cssClass, new scout.ButtonLayout(this));
  this.addField($button);
  // FIXME CGU: should we add a label? -> would make it possible to control the space left of the button using labelVisible, like it is possible with checkboxes
  this.addStatus();

  $button
    .on('click', this._onClick.bind(this))
    .on('mousedown', function(event) {
      // prevent focus validation on other field on mouse down. -> Safari workaround
      event.preventDefault();
    });

  if (this.menus && this.menus.length > 0) {
    this.menus.forEach(function(menu) {
      this.keyStrokeContext.registerKeyStroke(menu);
    }, this);
    if (this.label || !this.iconId) { // no indicator when _only_ the icon is visible
      $button.addClass('has-submenu');
    }
  }
  $button.unfocusable();
  this.rootKeyStrokeContext().registerKeyStroke(this.buttonKeyStroke);
};

scout.Button.prototype._onClick = function() {
  if (this.enabled) {
    this.doAction();
  }
};

scout.Button.prototype._remove = function() {
  scout.Button.parent.prototype._remove.call(this);
  this.rootKeyStrokeContext().unregisterKeyStroke(this.buttonKeyStroke);
};

/**
 * @returns {Boolean}
 *          <code>true</code> if the action has been performed or <code>false</code> if it
 *          has not been performed (e.g. when the button is not enabled).
 */
scout.Button.prototype.doAction = function() {
  if (!this.enabled || !this.visible) {
    return false;
  }

  if (this.displayStyle === scout.Button.DisplayStyle.TOGGLE) {
    this.setSelected(!this.selected);
  } else if (this.menus.length > 0) {
    this.togglePopup();
  } else {
    this.remoteHandler(this.id, 'clicked');
  }
  return true;
};

scout.Button.prototype.togglePopup = function() {
  if (this.popup) {
    this.popup.close();
  } else {
    this.popup = this._openPopup();
    this.popup.on('remove', function(event) {
      this.popup = null;
    }.bind(this));
  }
};

scout.Button.prototype._openPopup = function() {
  var popup = scout.create(scout.MenuBarPopup, {
    parent: this,
    menu: this
  });
  popup.render();
  return popup;
};

scout.Button.prototype.setSelected = function(selected) {
  this.selected = selected;
  if (this.rendered) {
    this._renderSelected(this.selected);
  }
  this.remoteHandler(this.id, 'selected', {
    selected: selected
  });
};

/**
 * @override
 */
scout.Button.prototype._renderProperties = function() {
  scout.Button.parent.prototype._renderProperties.call(this);
  this._renderIconId();
  this._renderSelected();
};

/**
 * @override
 */
scout.Button.prototype._renderEnabled = function() {
  scout.Button.parent.prototype._renderEnabled.call(this);
  if (this.displayStyle === scout.Button.DisplayStyle.LINK) {
    this.$field.setTabbable(this.enabled);
  }
};

scout.Button.prototype._renderSelected = function() {
  if (this.displayStyle === scout.Button.DisplayStyle.TOGGLE) {
    this.$field.toggleClass('selected', this.selected);
  }
};

/**
 * @override
 */
scout.Button.prototype._renderLabel = function(label) {
  this._$label.textOrNbsp(scout.strings.removeAmpersand(label));
};

/**
 * Adds an image or font-based icon to the button by adding either an IMG or SPAN element to the button.
 */
scout.Button.prototype._renderIconId = function() {
  this.$field.icon(this.iconId);
  if (this.iconId) {
    var $icon = this.$field.data('$icon');
    $icon.toggleClass('with-label', !! this.label);
  }
};

scout.Button.prototype._syncKeyStroke = function(keyStroke) {
  this.keyStroke = keyStroke;
  this.buttonKeyStroke.parseAndSetKeyStroke(this.keyStroke);
};
