A component defines and names a Data Class.

Within this body:

``` html
<body data-component="root: Pandemic">
</body>
```

The Data Class "Pandemic" is defined. The instance is called "root". Where ever variables prefixed with "root." occur, 
they're added to the data class, with type "string" unless defined otherwise.
If there is another component defined within a component, such as here:

``` html
<body data-component="root: Pandemic">
  <div data-component="user: User">
  </div>
</body>
```

Then a new Class is defined, and the parent class gets an instance variable with given type and name (here User and user).

There are also data-collections.

``` html
<div class="player-list" data-collection="root.players">
    <div class="player-item" data-collection-item="player"></div>
</div>
```

Here, players is a list variable in players. The data-collection-item functions as a component for player. 
