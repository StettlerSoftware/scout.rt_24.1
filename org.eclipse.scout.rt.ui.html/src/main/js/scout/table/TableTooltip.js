scout.TableTooltip = function() {
  scout.TableTooltip.parent.call(this);
};
scout.inherits(scout.TableTooltip, scout.Tooltip);

scout.TableTooltip.prototype._init = function(options) {
  scout.TableTooltip.parent.prototype._init.call(this, options);

  this.table = options.table;
};

scout.TableTooltip.prototype._render = function($parent) {
  scout.TableTooltip.parent.prototype._render.call(this, $parent);

  this._rowOrderChangedFunc = function(event) {
    if (event.animating) {
      // row is only set while animating
      if (event.row === this.row) {
        this.position();
      }
    } else {
      this.position();
    }
  }.bind(this);
  this.table.events.on(scout.Table.GUI_EVENT_ROW_ORDER_CHANGED, this._rowOrderChangedFunc);
};

scout.TableTooltip.prototype._remove = function() {
  scout.TableTooltip.parent.prototype._remove.call(this);
  this.table.events.off(scout.Table.GUI_EVENT_ROW_ORDER_CHANGED, this._rowOrderChangedFunc);
};
