(function () {
  'use strict';

  function toNode(value) {
    return value && typeof value.nodeType === 'number'
      ? value
      : document.createTextNode(String(value));
  }

  if (!Element.prototype.replaceChildren) {
    Element.prototype.replaceChildren = function () {
      while (this.firstChild) {
        this.removeChild(this.firstChild);
      }
      for (var index = 0; index < arguments.length; index += 1) {
        this.appendChild(toNode(arguments[index]));
      }
    };
  }

  if (!Element.prototype.append) {
    Element.prototype.append = function () {
      for (var index = 0; index < arguments.length; index += 1) {
        this.appendChild(toNode(arguments[index]));
      }
    };
  }
}());
