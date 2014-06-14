var demoApp = (function () {
  // this is a a small d3 tween function for nice looking counts
  function numberTween(d) {
    var el = d3.select(this);
    var formats = (el.attr("data-format") || "0,.2f|0,.4s").split("|");
    var small = d3.format(formats[0]);
    var kilo = d3.format(formats[1]);
    var o = el.attr("data-oldvalue") || 0;
    var i = d3.interpolate(o, d);
    el.attr("data-oldvalue", d);
    return function (t) {
      el.text((i(t) < 1000 ? small : kilo)(i(t)));
    };
  }

  // the BASE is only necessary if your webapp is hosted on a different system
  var BASE = "http://localhost:8080";

  // the actual app
  var app = angular.module('project', ['ngRoute', 'ngResource'])
      .config(function ($routeProvider) {
        $routeProvider
            .when('/', { templateUrl: 'main.html' })
            .when('/:filter/:value', { controller: 'FilterController', templateUrl: 'filtered.html' })
            .otherwise({ redirectTo: '/' });
      })
      .directive('d3Transition', function () {
        // this is the factory for the d3 tween transition, used in the template
        return function (scope, element, attrs) {
          attrs.$observe('count', function (count) {
            var old = d3.select(element[0]).attr("data-oldvalue") || 0;
            d3.select(element[0])
                .data([count])
                .style("color", old <= count ? "blue" : "red")
                .transition()
                .duration(2000)
                .style("color", "black")
                .tween('text', numberTween);
          });
        };
      })
      .run(function($http) {
        $http.defaults.headers.common.Authorization = 'APITOKEN 1c182c7f-40f0-45ca-8d55-7c5fad930173';
      })
      .filter('escape', function () { return encodeURIComponent; })
      .factory('Score', ['$resource', function ($resource) {
        return $resource(BASE + '/1/query/:trend')
      }])
      .factory('PageTrend', ['$resource', function ($resource) {
        return $resource(BASE + '/1/query/PageTrend', {count: 100})
      }])
      .factory('ActionTrend', ['$resource', function ($resource) {
        return $resource(BASE + '/1/query/ActionTrend', {count: 100})
      }])
      .factory('DomainTrend', ['$resource', function ($resource) {
        return $resource(BASE + '/1/query/DomainTrend', {count: 100})
      }])
      .factory('FilteredTrend', ['$resource', function ($resource) {
        return $resource(BASE + '/1/query/PageActionDomainTrend', {count: 100})
      }]);

  // this controller ensures the correct menu entry is hilighted
  app.controller('MenuController', function MenuController($scope, $location) {
    $scope.isActive = function (viewLocation) {
      return viewLocation === $location.path();
    };
  });

  /**
   * Main Page Controllers
   */
  app.controller('PageTrendController', function ($scope, $timeout, PageTrend) {
    var promise;
    var update = function () {
      PageTrend.get(function (result) { $scope.trend = result.trend; })
          .$promise.then(function () { promise = $timeout(update, 2000); });
    };
    $scope.$on('$destroy', function () { $timeout.cancel(promise); });
    update();
  });
  app.controller('ActionTrendController', function ($scope, $timeout, ActionTrend) {
    var promise;
    var update = function () {
      ActionTrend.get(function (result) { $scope.trend = result.trend; })
          .$promise.then(function () { promise = $timeout(update, 2000); });
    };
    $scope.$on('$destroy', function () { $timeout.cancel(promise); });
    update();
  });
  app.controller('DomainTrendController', function ($scope, $timeout, DomainTrend) {
    var promise;
    var update = function () {
      DomainTrend.get(function (result) { $scope.trend = result.trend; })
          .$promise.then(function () { promise = $timeout(update, 2000); });
    };
    $scope.$on('$destroy', function () { $timeout.cancel(promise); });
    update();
  });

  // you will have to make extra efforts if your page :id is a real url like /page/99 instead of page-99
  // I ran into this issue when playing with angular.js, see
  // https://groups.google.com/forum/#!topic/angular/Gna-zWBJhIE
  app.controller('FilterController', function ($scope, $timeout, $routeParams, FilteredTrend) {
    var promise;
    var update = function () {
      var params = {};
      params[$routeParams["filter"]] = $routeParams["value"];
      FilteredTrend.get(params,function (result) {
        $scope.filter = $routeParams["filter"];
        $scope.value = $routeParams["value"];
        $scope.trend = result.trend.map(function (e) {
          // to ensure in-place rendering we need a key that can be hashed (combined keys here)
          e.key = e.keys.join("-");
          return e;
        });
      }).$promise.then(function () { promise = $timeout(update, 2000); });
    };
    $scope.$on('$destroy', function () { $timeout.cancel(promise); });
    update();
  });
  // the following controller takes care of the score updates for the filter page (main score)
  app.controller('FilterHeaderController', function ($scope, $routeParams, $timeout, Score) {
    var trend = $routeParams.filter.charAt(0).toUpperCase() + $routeParams.filter.slice(1) + "Trend";
    var params = {"trend": trend};
    params[$routeParams["filter"]] = $routeParams["value"];
    var update = function () {
      Score.get(params,function (result) {
        $scope.score = result.trend[0].score;
      }).$promise.then(function () { promise = $timeout(update, 2000); });
    };
    $scope.$on('$destroy', function () { $timeout.cancel(promise); });
    update();
  });

  return app;
})();
