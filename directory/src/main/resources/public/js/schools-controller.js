function SchoolsController($scope, views){
	$scope.views = views;
	views.open('list', 'table-list');
}