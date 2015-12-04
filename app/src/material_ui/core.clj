(ns material-ui.core)

(def material-tags
  '[AppBar
    AppCanvas
    Avatar
    ;Circle
    Checkbox
    DatePicker
    ;DialogWindow
    Dialog
    DropDownIcon
    DropDownMenu
    EnhancedButton
    FlatButton
    FloatingActionButton
    ;FocusRipple
    ;FontIcon
    GridList
    GridTile
    IconButton
    IconMenu
    ;InkBar
    ;Input
    LeftNav
    MenuItem
    Menu
    Overlay
    Paper
    RadioButton
    RadioButtonGroup
    RaisedButton
    SelectField
    Slider
    ;SlideIn
    Snackbar
    SvgIcon
    Tab
    ;TabTemplate
    Tabs
    TableHeader
    TableHeaderColumn
    ;TableRowsItem
    ;TableRows
    TableRow
    TableRowColumn
    TextField
    TimePicker
    Toggle
    ToolbarGroup
    ToolbarSeparator
    ToolbarTitle
    Toolbar
    Tooltip
    ;TouchRipple
    ])

(defn material-ui-react-import [tname]
  `(def ~tname (reagent.core/adapt-react-class (aget js/MaterialUI ~(name tname)))))

(defmacro export-material-ui-react-classes []
  `(do ~@(map material-ui-react-import material-tags)))

