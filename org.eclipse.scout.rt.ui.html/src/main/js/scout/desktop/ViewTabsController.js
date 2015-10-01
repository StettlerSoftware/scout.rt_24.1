/**
 * Controller to interact with 'view tab bar'.
 */
scout.ViewTabsController = function(desktop) {
  this._desktop = desktop;
  this._selectedViewTab;
  this._viewTabs = [];
  this._viewTabMap = {}; // [key=viewId, value=DesktopViewTab instance]
};

/**
 * Creates and renders a view tab for the given view.
 */
scout.ViewTabsController.prototype.createAndRenderViewTab = function(view, position) {
  // Create the view tab.
  var viewTab = scout.create(scout.DesktopViewTab, {
    parent: this._desktop,
    view: view,
    $bench: this._desktop.$bench,
    viewTabController: this
  });
  var viewId = view.id;

  // Register lifecycle listener on view tab.
  viewTab.on('tabClicked', this.selectViewTab.bind(this));
  viewTab.on('remove', this._removeViewTab.bind(this, viewTab, viewId));

  var index = position;
  var parentViewTab = this.viewTab(viewTab._view.parent);
  if (parentViewTab) {
    index = this._viewTabs.indexOf(parentViewTab) + this._calculateExactPosition(parentViewTab, index) + 1;
  } else {
    index = this._calculateExactPosition(this._desktop, index);
  }

  // Register the view tab.
  scout.arrays.insert(this._viewTabs, viewTab, index);
  this._viewTabMap[viewId] = viewTab;

  // Render the view tab.
  if (this._desktop._hasTaskBar()) {
    viewTab.render(this._desktop._$viewTabBar);
  }

  return viewTab;
};

scout.ViewTabsController.prototype._calculateExactPosition = function(parent, position) {
  if (position === 0) {
    return 0;
  } else {
    var tabs = position || parent.views.length;
    var searchUntil = position || parent.views.length;
    for (var i = 0; i < searchUntil; i++) {
      tabs = tabs + this._calculateExactPosition(parent.views[i]);
    }
    return tabs;
  }
};
/**
 * Method invoked once the given view tab is removed from DOM.
 */
scout.ViewTabsController.prototype._removeViewTab = function(viewTab, viewId) {
  // Unregister the view tab.
  var viewTabIndexBefore = this._viewTabs.indexOf(viewTab) - 1;
  scout.arrays.remove(this._viewTabs, viewTab);
  delete this._viewTabMap[viewId];

  // Select next available view tab.
  // FIXME DWI: (activeForm) use activeForm here or when no form is active, show outline again (from A.WE)
  if (this._selectedViewTab === viewTab) {
    var parentViewTab = this.viewTab(viewTab._view.parent);
    if (viewTabIndexBefore >= 0) {
      this.selectViewTab(this._viewTabs[viewTabIndexBefore]);
    } else {
      this._desktop.bringOutlineToFront(this._desktop.outline);
    }
  }

  this._desktop._layoutTaskBar();
};

/**
 * Selects the given view tab and attaches its associated view.
 */
scout.ViewTabsController.prototype.selectViewTab = function(viewTab) {
  if (this._selectedViewTab === viewTab) {
    return;
  }

  // Hide outline content.
  this._desktop._sendNavigationToBack();
  this._desktop._detachOutlineContent();

  // Deselect the current selected tab.
  this.deselectViewTab();

  // set _selectedViewTab before selecting view tab. if this is not done before there is a problem when refreshing the webpage.
  // parent is not set as selected, but rendered, before child-> child is rendered into same view because parent is not deselect.
  // parent viewTab.select calls rendering of child.
  this._selectedViewTab = viewTab;
  // Select the new view tab.
  viewTab.select();

  // Invalidate layout and focus.
  this._desktop._layoutTaskBar();
};

/**
 * Deselects the currently selected view tab.
 */
scout.ViewTabsController.prototype.deselectViewTab = function() {
  if (!this._selectedViewTab) {
    return;
  }

  this._selectedViewTab.deselect();
  this._selectedViewTab = null;
};

/**
 * Returns the view tab associated with the given view.
 */
scout.ViewTabsController.prototype.viewTab = function(view) {
  return this._viewTabMap[view.id];
};

/**
 * Returns the all view tabs.
 */
scout.ViewTabsController.prototype.viewTabs = function() {
  return this._viewTabs;
};

/**
 * Returns the selected view tab.
 */
scout.ViewTabsController.prototype.selectedViewTab = function() {
  return this._selectedViewTab;
};

/**
 * Returns the number of view tabs.
 */
scout.ViewTabsController.prototype.viewTabCount = function() {
  return this._viewTabs.length;
};

/**
 * Selects the last view tab.
 */
scout.ViewTabsController.prototype._selectLastViewTab = function() {
  if (this._viewTabs.length > 0) {
    this.selectViewTab(this._viewTabs[this._viewTabs.length - 1]);
  } else {
    this.deselectViewTab();

    this._desktop._attachOutlineContent();
    this._desktop._bringNavigationToFront();
  }
};
